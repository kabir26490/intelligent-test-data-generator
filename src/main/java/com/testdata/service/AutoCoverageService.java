package com.testdata.service;

import com.testdata.model.CoverageReport;
import com.testdata.model.CoverageRequest;
import com.testdata.model.GenerateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AutoCoverageService {

    @Autowired
    private DataGeneratorService dataGeneratorService;

    public CoverageReport generateCoverage(CoverageRequest request) {

        // Step A: Ask AI to identify scenarios + schemas
        // Stub returns hardcoded TMF622 scenarios so the full pipeline works end-to-end.
        // Replace callAI() body with real HTTP call to Claude/OpenAI when key is wired in.
        List<Map<String, Object>> aiScenarios = callAI(request.getParsedSpec(), request.getApiTitle());

        // Step B: Feed each scenario schema into existing generator
        List<CoverageReport.CoverageScenario> built = new ArrayList<>();
        int generated = 0;

        for (Map<String, Object> raw : aiScenarios) {
            CoverageReport.CoverageScenario scenario = new CoverageReport.CoverageScenario();
            scenario.setName((String) raw.get("name"));
            scenario.setBusinessDescription((String) raw.get("description"));
            scenario.setTmfRule((String) raw.getOrDefault("tmfRule", ""));
            scenario.setCategory((String) raw.getOrDefault("category", "happy_path"));

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = (Map<String, Object>) raw.get("schema");

                GenerateRequest genReq = new GenerateRequest();
                genReq.setCount(request.getRecordsPerScenario() > 0 ? request.getRecordsPerScenario() : 3);
                genReq.setSchema(schema);

                List<Map<String, Object>> data = dataGeneratorService.generate(genReq);
                scenario.setGeneratedData(new ArrayList<>(data));
                scenario.setGenerated(true);
                generated++;
            } catch (Exception e) {
                scenario.setGenerated(false);
                scenario.setGeneratedData(Collections.emptyList());
            }

            built.add(scenario);
        }

        // Step C: Build coverage report
        CoverageReport report = new CoverageReport();
        report.setApiTitle(request.getApiTitle());
        report.setTotalScenariosIdentified(aiScenarios.size());
        report.setTotalScenariosGenerated(generated);
        report.setCoverageScore(aiScenarios.isEmpty() ? 0 : (generated * 100) / aiScenarios.size());
        report.setScenarios(built);
        report.setGaps(extractGaps(aiScenarios));

        return report;
    }

    // ─────────────────────────────────────────────────────────────
    // STUB — replace this method body when AI key is wired in.
    // Contract: returns List of maps, each map has:
    //   name        (String)
    //   description (String)
    //   tmfRule     (String)
    //   category    (String)  happy_path | edge_case | negative
    //   schema      (Map)     valid GeneratorRequest schema
    //   isGap       (Boolean) true = AI says this is missing coverage
    // ─────────────────────────────────────────────────────────────
    private List<Map<String, Object>> callAI(String spec, String apiTitle) {

        List<Map<String, Object>> scenarios = new ArrayList<>();

        scenarios.add(buildScenario(
            "Standard postpaid order — happy path",
            "A new postpaid customer places a product order successfully",
            "TMF622 §4.1",
            "happy_path",
            false,
            Map.of(
                "id", "tmf-id",
                "state", Map.of("type", "enum", "values", List.of("acknowledged")),
                "orderDate", "datetime",
                "description", "sentence",
                "priority", Map.of("type", "number", "min", 1, "max", 4),
                "relatedParty", Map.of(
                    "type", "array", "minItems", 1, "maxItems", 2,
                    "schema", Map.of(
                        "id", "uuid",
                        "role", Map.of("type", "enum", "values", List.of("customer", "seller")),
                        "name", "fullName",
                        "@type", Map.of("type", "constant", "value", "RelatedParty")
                    )
                ),
                "@type", Map.of("type", "constant", "value", "ProductOrder"),
                "@baseType", Map.of("type", "constant", "value", "Entity")
            )
        ));

        scenarios.add(buildScenario(
            "Order for already-active product",
            "Customer attempts to order a product already active on their account",
            "TMF622 §5.2 conflict rule",
            "negative",
            false,
            Map.of(
                "id", "tmf-id",
                "state", Map.of("type", "enum", "values", List.of("acknowledged")),
                "orderDate", "datetime",
                "note", Map.of(
                    "type", "array", "minItems", 1, "maxItems", 1,
                    "schema", Map.of(
                        "text", Map.of("type", "constant", "value", "duplicate product conflict"),
                        "date", "datetime"
                    )
                ),
                "@type", Map.of("type", "constant", "value", "ProductOrder")
            )
        ));

        scenarios.add(buildScenario(
            "Order completion with all timestamps",
            "A completed order with correct orderDate, completionDate and state",
            "TMF622 §4.3.1 state machine",
            "happy_path",
            false,
            Map.of(
                "id", "tmf-id",
                "state", Map.of("type", "enum", "values", List.of("completed")),
                "orderDate", "datetime",
                "completionDate", "datetime",
                "priority", Map.of("type", "number", "min", 1, "max", 1),
                "@type", Map.of("type", "constant", "value", "ProductOrder")
            )
        ));

        scenarios.add(buildScenario(
            "Cancellation request mid-order",
            "Order in inProgress state receives a cancellation",
            "TMF622 §4.3.3 cancellation flow",
            "edge_case",
            false,
            Map.of(
                "id", "tmf-id",
                "state", Map.of("type", "enum", "values", List.of("inProgress")),
                "orderDate", "datetime",
                "cancellationDate", "datetime",
                "cancellationReason", "sentence",
                "@type", Map.of("type", "constant", "value", "ProductOrder")
            )
        ));

        // GAP — AI flagged this as missing coverage
        scenarios.add(buildScenario(
            "VIP customer concurrent order conflict",
            "High-priority customer has two simultaneous orders for overlapping products",
            "TMF622 §6.1 concurrent modification",
            "edge_case",
            true,
            Map.of(
                "id", "tmf-id",
                "state", Map.of("type", "enum", "values", List.of("pending")),
                "priority", Map.of("type", "number", "min", 1, "max", 1),
                "orderDate", "datetime",
                "@type", Map.of("type", "constant", "value", "ProductOrder")
            )
        ));

        return scenarios;
    }

    private Map<String, Object> buildScenario(String name, String desc, String rule,
                                               String category, boolean isGap,
                                               Map<String, Object> schema) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("description", desc);
        m.put("tmfRule", rule);
        m.put("category", category);
        m.put("isGap", isGap);
        m.put("schema", schema);
        return m;
    }

    private List<String> extractGaps(List<Map<String, Object>> scenarios) {
        List<String> gaps = new ArrayList<>();
        for (Map<String, Object> s : scenarios) {
            if (Boolean.TRUE.equals(s.get("isGap"))) {
                gaps.add(s.get("name") + " — " + s.get("description"));
            }
        }
        return gaps;
    }
}
