package com.testdata.controller;

import com.testdata.service.OpenApiService;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for OpenAPI spec parsing and schema extraction.
 * Provides endpoints to parse specs, list operations, and convert to generator schemas.
 */
@RestController
@RequestMapping("/api/openapi")
@CrossOrigin(origins = "*")
public class OpenApiController {

    private final OpenApiService openApiService;

    public OpenApiController(OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    /**
     * Parse an OpenAPI spec and list all available operations.
     *
     * Request body: { "spec": "<yaml or json string>" }
     * Response: { "operations": [...], "title": "...", "version": "..." }
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseSpec(@RequestBody Map<String, String> body) {
        try {
            String specContent = body.get("spec");
            if (specContent == null || specContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'spec' field in request body."));
            }

            OpenAPI spec = openApiService.parseSpec(specContent);
            List<Map<String, String>> operations = openApiService.listOperations(spec);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", spec.getInfo() != null && spec.getInfo().getTitle() != null
                    ? spec.getInfo().getTitle() : "Untitled API");
            result.put("version", spec.getInfo() != null && spec.getInfo().getVersion() != null
                    ? spec.getInfo().getVersion() : "unknown");
            result.put("operationCount", operations.size());
            result.put("operations", operations);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI spec: " + e.getMessage()));
        }
    }

    /**
     * Extract a schema for a specific operation and direction (request/response).
     *
     * Request body: { "spec": "...", "operationId": "...", "direction": "request|response", "count": 10 }
     * Response: { "count": 10, "schema": { ... } }
     */
    @PostMapping("/schema")
    public ResponseEntity<?> extractSchema(@RequestBody Map<String, Object> body) {
        try {
            String specContent = (String) body.get("spec");
            String operationId = (String) body.get("operationId");
            String direction = (String) body.get("direction");
            int count = 10;

            if (specContent == null || specContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'spec' field."));
            }
            if (operationId == null || operationId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'operationId' field."));
            }
            if (direction == null || direction.isBlank()) {
                direction = "response";
            }

            Object countObj = body.get("count");
            if (countObj instanceof Number) {
                count = ((Number) countObj).intValue();
            }

            OpenAPI spec = openApiService.parseSpec(specContent);
            Map<String, Object> payload = openApiService.extractSchema(spec, operationId, direction, count);

            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to extract schema: " + e.getMessage()));
        }
    }
}
