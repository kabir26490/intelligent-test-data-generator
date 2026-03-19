package com.testdata.model;

import java.util.List;

public class CoverageReport {

    private String apiTitle;
    private int totalScenariosIdentified;
    private int totalScenariosGenerated;
    private int coverageScore;
    private List<CoverageScenario> scenarios;
    private List<String> gaps;

    public static class CoverageScenario {
        private String name;
        private String businessDescription;
        private String tmfRule;
        private String category;
        private List<Object> generatedData;
        private boolean generated;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBusinessDescription() { return businessDescription; }
        public void setBusinessDescription(String d) { this.businessDescription = d; }
        public String getTmfRule() { return tmfRule; }
        public void setTmfRule(String tmfRule) { this.tmfRule = tmfRule; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<Object> getGeneratedData() { return generatedData; }
        public void setGeneratedData(List<Object> data) { this.generatedData = data; }
        public boolean isGenerated() { return generated; }
        public void setGenerated(boolean generated) { this.generated = generated; }
    }

    public String getApiTitle() { return apiTitle; }
    public void setApiTitle(String apiTitle) { this.apiTitle = apiTitle; }
    public int getTotalScenariosIdentified() { return totalScenariosIdentified; }
    public void setTotalScenariosIdentified(int n) { this.totalScenariosIdentified = n; }
    public int getTotalScenariosGenerated() { return totalScenariosGenerated; }
    public void setTotalScenariosGenerated(int n) { this.totalScenariosGenerated = n; }
    public int getCoverageScore() { return coverageScore; }
    public void setCoverageScore(int n) { this.coverageScore = n; }
    public List<CoverageScenario> getScenarios() { return scenarios; }
    public void setScenarios(List<CoverageScenario> scenarios) { this.scenarios = scenarios; }
    public List<String> getGaps() { return gaps; }
    public void setGaps(List<String> gaps) { this.gaps = gaps; }
}
