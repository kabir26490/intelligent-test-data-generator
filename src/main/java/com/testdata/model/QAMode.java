package com.testdata.model;

import java.util.List;

public class QAMode {
    private Boolean edgeCases = false;
    private Double edgeCaseRatio = 0.1;
    private List<String> include;

    public Boolean getEdgeCases() { return edgeCases; }
    public void setEdgeCases(Boolean edgeCases) { this.edgeCases = edgeCases; }
    public Double getEdgeCaseRatio() { return edgeCaseRatio; }
    public void setEdgeCaseRatio(Double edgeCaseRatio) { this.edgeCaseRatio = edgeCaseRatio; }
    public List<String> getInclude() { return include; }
    public void setInclude(List<String> include) { this.include = include; }
}