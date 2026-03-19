package com.testdata.controller;

import com.testdata.model.CoverageReport;
import com.testdata.model.CoverageRequest;
import com.testdata.model.GenerateRequest;
import com.testdata.service.AutoCoverageService;
import com.testdata.service.DataGeneratorService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.*;

/**
 * REST API controller for test data generation.
 * 
 * Provides endpoints to:
 * - POST /api/generate: Generate test data based on schema
 * - GET /api/health: Health check
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GeneratorController {
    private final DataGeneratorService service;
    private final AutoCoverageService autoCoverageService;

    public GeneratorController(DataGeneratorService service, AutoCoverageService autoCoverageService) {
        this.service = service;
        this.autoCoverageService = autoCoverageService;
    }

    /**
     * Generate test data based on provided schema and rules.
     * 
     * @param request GenerateRequest containing schema, count, computed fields, and QA mode
     * @return List of generated Map objects (records) as JSON
     * 
     * @throws Exception caught and returned as 400 Bad Request with error message
     * 
     * Example request:
     * {
     *   "count": 10,
     *   "schema": {"id": "uuid", "name": "fullName"},
     *   "computedFields": {"display_name": "name"},
     *   "qaMode": {"edgeCases": true, "edgeCaseRatio": 0.2}
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateRequest request) {
        try {
            List<Map<String, Object>> data = service.generate(request);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            // Return error response with 400 status and error message
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check endpoint.
     * Verify the application is running and responsive.
     * 
     * @return Status map with status and version
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "version", "1.0.0");
    }

    @PostMapping("/coverage")
    public ResponseEntity<?> generateCoverage(@RequestBody CoverageRequest request) {
        try {
            CoverageReport report = autoCoverageService.generateCoverage(request);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}