package com.xfwang.govdata.datasource.application.options.view;

import java.util.List;

public class DataSourceOptionsView {
    private final List<OptionView> environments;
    private final List<OptionView> accessModes;
    private final List<OptionView> classifications;
    private final List<OptionView> securityLevels;
    private final List<OptionView> statuses;
    private final List<OptionView> testStatuses;
    private final List<OptionView> connectionTestReasons;

    public DataSourceOptionsView(List<OptionView> environments, List<OptionView> accessModes,
                                 List<OptionView> classifications, List<OptionView> securityLevels,
                                 List<OptionView> statuses, List<OptionView> testStatuses,
                                 List<OptionView> connectionTestReasons) {
        this.environments = environments == null ? List.of() : environments;
        this.accessModes = accessModes == null ? List.of() : accessModes;
        this.classifications = classifications == null ? List.of() : classifications;
        this.securityLevels = securityLevels == null ? List.of() : securityLevels;
        this.statuses = statuses == null ? List.of() : statuses;
        this.testStatuses = testStatuses == null ? List.of() : testStatuses;
        this.connectionTestReasons = connectionTestReasons == null ? List.of() : connectionTestReasons;
    }

    public List<OptionView> getEnvironments() { return environments; }
    public List<OptionView> getAccessModes() { return accessModes; }
    public List<OptionView> getClassifications() { return classifications; }
    public List<OptionView> getSecurityLevels() { return securityLevels; }
    public List<OptionView> getStatuses() { return statuses; }
    public List<OptionView> getTestStatuses() { return testStatuses; }
    public List<OptionView> getConnectionTestReasons() { return connectionTestReasons; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<OptionView> environments = List.of();
        private List<OptionView> accessModes = List.of();
        private List<OptionView> classifications = List.of();
        private List<OptionView> securityLevels = List.of();
        private List<OptionView> statuses = List.of();
        private List<OptionView> testStatuses = List.of();
        private List<OptionView> connectionTestReasons = List.of();

        public Builder environments(List<OptionView> environments) { this.environments = environments; return this; }
        public Builder accessModes(List<OptionView> accessModes) { this.accessModes = accessModes; return this; }
        public Builder classifications(List<OptionView> classifications) { this.classifications = classifications; return this; }
        public Builder securityLevels(List<OptionView> securityLevels) { this.securityLevels = securityLevels; return this; }
        public Builder statuses(List<OptionView> statuses) { this.statuses = statuses; return this; }
        public Builder testStatuses(List<OptionView> testStatuses) { this.testStatuses = testStatuses; return this; }
        public Builder connectionTestReasons(List<OptionView> connectionTestReasons) { this.connectionTestReasons = connectionTestReasons; return this; }

        public DataSourceOptionsView build() {
            return new DataSourceOptionsView(environments, accessModes, classifications, securityLevels,
                    statuses, testStatuses, connectionTestReasons);
        }
    }
}
