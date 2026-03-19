package com.testdata.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testdata.service.OpenApiService;
import com.testdata.service.RedisTrialService;
import com.testdata.service.RedisTrialService.TrialExhaustedResult;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for OpenAPI spec parsing and schema extraction.
 *
 * Trial enforcement (feature/redis-trial-enforcement branch):
 *   - Each user (X-Api-Key header or fallback IP) gets 5 free unique parses.
 *   - Repeated parses of the same spec are served from Redis cache (free, no trial count).
 *   - After 5 unique parses, HTTP 402 is returned.
 *   - Set dev.trial.disabled=true in application.properties to bypass during local dev.
 */
@RestController
@RequestMapping("/api/openapi")
@CrossOrigin(origins = "*")
public class OpenApiController {

    private final OpenApiService openApiService;
    private final RedisTrialService trialService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenApiController(OpenApiService openApiService, RedisTrialService trialService) {
        this.openApiService = openApiService;
        this.trialService = trialService;
    }

    // -------------------------------------------------------------------------
    // Helper: resolve API key from header or fallback to IP
    // -------------------------------------------------------------------------
    private String resolveApiKey(HttpServletRequest request) {
        String key = request.getHeader("X-Api-Key");
        if (key != null && !key.isBlank()) return key.trim();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return "anon:" + ip;
    }

    // -------------------------------------------------------------------------
    // POST /api/openapi/parse
    // -------------------------------------------------------------------------

    /**
     * Parse an OpenAPI spec and list all available operations.
     *
     * Request body: { "spec": "<yaml or json string>" }
     * Response: { "operations": [...], "title": "...", "version": "...", "remainingTrials": N }
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseSpec(@RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        try {
            String specContent = body.get("spec");
            if (specContent == null || specContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'spec' field in request body."));
            }

            // Validate spec size
            String sizeError = trialService.validateSpecSize(specContent);
            if (sizeError != null) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", sizeError));
            }

            String apiKey = resolveApiKey(request);

            // Cache hit — return without counting trial
            String cached = trialService.getCachedResult("parse:" + specContent);
            if (cached != null) {
                Map<String, Object> cachedResult = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                cachedResult.put("cached", true);
                cachedResult.put("remainingTrials", trialService.remainingTrials(apiKey));
                return ResponseEntity.ok(cachedResult);
            }

            // Trial check — consume one parse credit
            TrialExhaustedResult exhausted = trialService.tryConsumeTrial(apiKey);
            if (exhausted != null) {
                return ResponseEntity.status(402).body(exhausted.toResponseBody());
            }

            // Parse
            OpenAPI spec = openApiService.parseSpec(specContent);
            List<Map<String, String>> operations = openApiService.listOperations(spec);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title",          spec.getInfo() != null && spec.getInfo().getTitle() != null
                    ? spec.getInfo().getTitle() : "Untitled API");
            result.put("version",        spec.getInfo() != null && spec.getInfo().getVersion() != null
                    ? spec.getInfo().getVersion() : "unknown");
            result.put("operationCount", operations.size());
            result.put("operations",     operations);
            result.put("remainingTrials", trialService.remainingTrials(apiKey));

            // Cache the result
            trialService.cacheResult("parse:" + specContent, result);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI spec: " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/openapi/schema
    // -------------------------------------------------------------------------

    /**
     * Extract a schema for a specific operation and direction (request/response).
     *
     * Request body: { "spec": "...", "operationId": "...", "direction": "request|response", "count": 10 }
     * Response: { "count": 10, "schema": { ... } }
     *
     * Note: schema extraction is counted as one trial parse (same spec hash as /parse).
     * If the same spec was already parsed (cached), no additional credit is consumed.
     */
    @PostMapping("/schema")
    public ResponseEntity<?> extractSchema(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        try {
            String specContent = (String) body.get("spec");
            String operationId = (String) body.get("operationId");
            String direction   = (String) body.get("direction");
            int count = 10;

            if (specContent == null || specContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'spec' field."));
            }
            if (operationId == null || operationId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'operationId' field."));
            }
            if (direction == null || direction.isBlank()) direction = "response";

            Object countObj = body.get("count");
            if (countObj instanceof Number) count = ((Number) countObj).intValue();

            // Validate spec size
            String sizeError = trialService.validateSpecSize(specContent);
            if (sizeError != null) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", sizeError));
            }

            String apiKey  = resolveApiKey(request);
            String cacheKey = "schema:" + specContent + ":" + operationId + ":" + direction + ":" + count;

            // Cache hit
            String cached = trialService.getCachedResult(cacheKey);
            if (cached != null) {
                Map<String, Object> cachedResult = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                cachedResult.put("cached", true);
                return ResponseEntity.ok(cachedResult);
            }

            // Trial check — only counts if the spec parse result is also not cached
            boolean parseAlreadyCached = trialService.getCachedResult("parse:" + specContent) != null;
            if (!parseAlreadyCached) {
                TrialExhaustedResult exhausted = trialService.tryConsumeTrial(apiKey);
                if (exhausted != null) {
                    return ResponseEntity.status(402).body(exhausted.toResponseBody());
                }
            }

            OpenAPI spec = openApiService.parseSpec(specContent);
            Map<String, Object> payload = openApiService.extractSchema(spec, operationId, direction, count);

            // Cache
            trialService.cacheResult(cacheKey, payload);

            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to extract schema: " + e.getMessage()));
        }
    }
}
