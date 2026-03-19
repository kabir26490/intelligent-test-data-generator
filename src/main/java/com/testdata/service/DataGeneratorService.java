package com.testdata.service;

import com.testdata.generator.DataTypeGenerator;
import com.testdata.model.*;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Core service for test data generation.
 * 
 * Orchestrates the data generation pipeline:
 * 1. Parse request parameters and seed the random generator if provided
 * 2. Generate normal (non-edge case) records according to schema
 * 3. Apply computed fields using business rules
 * 4. Generate edge cases if QA mode is enabled
 * 5. Shuffle results for realistic ordering
 * 
 * Each generated record is tagged with _qa_metadata for traceability.
 * 
 * @see DataTypeGenerator - Generates individual field values
 * @see BusinessRuleEngine - Applies computed fields
 * @see EdgeCaseGenerator - Creates boundary value test cases
 */
@Service
public class DataGeneratorService {
    private final DataTypeGenerator dataTypeGenerator;
    private final BusinessRuleEngine businessRuleEngine;
    private final EdgeCaseGenerator edgeCaseGenerator;

    public DataGeneratorService(DataTypeGenerator dataTypeGenerator, BusinessRuleEngine businessRuleEngine, EdgeCaseGenerator edgeCaseGenerator) {
        this.dataTypeGenerator = dataTypeGenerator;
        this.businessRuleEngine = businessRuleEngine;
        this.edgeCaseGenerator = edgeCaseGenerator;
    }

    /**
     * Generate test data records according to the provided request.
     * 
     * Process flow:
     * 1. Set random seed if provided (for reproducibility)
     * 2. Calculate how many normal vs edge case records to generate
     * 3. Generate normal records with computed fields
     * 4. Add QA metadata to each normal record
     * 5. If QA mode enabled, generate edge cases and append
     * 6. Shuffle result list for realistic ordering
     * 
     * @param request The generation request containing schema and options
     * @return List of generated Map objects (records), each with _qa_metadata
     */
    public List<Map<String, Object>> generate(GenerateRequest request) {
        // Set seed if provided for reproducible data
        if (request.getSeed() != null) dataTypeGenerator.setSeed(request.getSeed());

        List<Map<String, Object>> result = new ArrayList<>();
        int normalCount = request.getCount();
        int edgeCaseCount = 0;

        // Determine edge case ratio if QA mode is enabled
        QAMode qaMode = request.getQaMode();
        if (qaMode != null && qaMode.getEdgeCases()) {
            edgeCaseCount = (int) (request.getCount() * qaMode.getEdgeCaseRatio());
            normalCount = request.getCount() - edgeCaseCount;
        }

        // Generate normal records
        for (int i = 0; i < normalCount; i++) {
            Map<String, Object> record = generateRecord(request.getSchema());
            
            // Apply computed fields (business rules) to this record
            if (request.getComputedFields() != null) {
                businessRuleEngine.applyComputedFields(record, request.getComputedFields());
            }
            
            // Add QA metadata for test traceability
            record.put("_qa_metadata", Map.of(
                "type", "normal",
                "business_rules_validated", request.getComputedFields() != null
            ));
            result.add(record);
        }

        // Generate edge cases if QA mode is enabled
        if (edgeCaseCount > 0) {
            result.addAll(edgeCaseGenerator.generateEdgeCases(
                request.getSchema(),
                request.getComputedFields(),
                edgeCaseCount,
                qaMode.getInclude()
            ));
        }

        // Shuffle results for realistic ordering (edge cases not always at end)
        Collections.shuffle(result);
        return result;
    }

    /**
     * Generate a single record by invoking the DataTypeGenerator for each field.
     * 
     * Maintains field order using LinkedHashMap.
     * Passes the partial record to enable field dependencies in future enhancements.
     * 
     * @param schema The schema map defining field names and types
     * @return A new Map representing one data record
     */
    private Map<String, Object> generateRecord(Map<String, Object> schema) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            record.put(entry.getKey(),
                dataTypeGenerator.generateValue(entry.getKey(), entry.getValue(), "en_US", record));
        }
        return record;
    }
}