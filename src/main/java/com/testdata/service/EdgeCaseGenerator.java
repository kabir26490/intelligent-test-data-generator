package com.testdata.service;

import com.testdata.generator.DataTypeGenerator;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Generates edge case test data for boundary value analysis.
 * 
 * Edge cases include minimum, maximum, and boundary values to ensure
 * applications handle extreme inputs correctly. QA teams use these records
 * to stress-test applications with extreme but valid inputs.
 * 
 * LIMITATION: Only 4 predefined edge case types supported.
 * Cannot define custom edge case strategies.
 */
@Service
public class EdgeCaseGenerator {
    private final DataTypeGenerator dataTypeGenerator;
    private final BusinessRuleEngine businessRuleEngine;

    public EdgeCaseGenerator(DataTypeGenerator dataTypeGenerator, BusinessRuleEngine businessRuleEngine) {
        this.dataTypeGenerator = dataTypeGenerator;
        this.businessRuleEngine = businessRuleEngine;
    }

    public List<Map<String, Object>> generateEdgeCases(
            Map<String, Object> schema,
            Map<String, String> computedFields,
            int count,
            List<String> includeTypes) {

        List<Map<String, Object>> edgeCases = new ArrayList<>();
        if (includeTypes == null) {
            includeTypes = Arrays.asList("minimum", "maximum", "boundary_minus", "boundary_plus");
        }

        for (String edgeType : includeTypes) {
            Map<String, Object> edgeCase = generateEdgeCase(schema, edgeType);
            if (computedFields != null) businessRuleEngine.applyComputedFields(edgeCase, computedFields);
            edgeCase.put("_qa_metadata", Map.of("type", "edge_case", "edge_case_type", edgeType));
            edgeCases.add(edgeCase);
            if (edgeCases.size() >= count) break;
        }
        return edgeCases;
    }

    private Map<String, Object> generateEdgeCase(Map<String, Object> schema, String edgeType) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            record.put(entry.getKey(), generateEdgeCaseValue(entry.getKey(), entry.getValue(), edgeType));
        }
        return record;
    }

    private Object generateEdgeCaseValue(String fieldName, Object fieldDef, String edgeType) {
        if (!(fieldDef instanceof Map)) {
            return dataTypeGenerator.generateValue(fieldName, fieldDef, "en_US", new HashMap<>());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> def = (Map<String, Object>) fieldDef;
        String type = (String) def.get("type");
        if (type == null) return null;

        switch (edgeType.toLowerCase()) {
            case "minimum":
            case "free_shipping_boundary_minus":
                return (type.equals("number") || type.equals("decimal")) && def.containsKey("min")
                    ? def.get("min") : 0;
            case "maximum":
                return (type.equals("number") || type.equals("decimal")) && def.containsKey("max")
                    ? def.get("max") : 999999;
            case "free_shipping_boundary_exact":
                return 100;
            default:
                return dataTypeGenerator.generateValue(fieldName, fieldDef, "en_US", new HashMap<>());
        }
    }
}