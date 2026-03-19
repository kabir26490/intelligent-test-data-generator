package com.testdata.model;

public class CoverageRequest {
    private String parsedSpec;
    private String apiTitle;
    private int recordsPerScenario;

    public String getParsedSpec() { return parsedSpec; }
    public void setParsedSpec(String parsedSpec) { this.parsedSpec = parsedSpec; }
    public String getApiTitle() { return apiTitle; }
    public void setApiTitle(String apiTitle) { this.apiTitle = apiTitle; }
    public int getRecordsPerScenario() { return recordsPerScenario; }
    public void setRecordsPerScenario(int recordsPerScenario) { this.recordsPerScenario = recordsPerScenario; }
}
