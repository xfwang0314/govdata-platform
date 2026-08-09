package com.xfwang.govdata.datasource.application.options.view;

public class OptionView {
    private final String value;
    private final String label;

    public OptionView(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() { return value; }
    public String getLabel() { return label; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String value;
        private String label;

        public Builder value(String value) { this.value = value; return this; }
        public Builder label(String label) { this.label = label; return this; }

        public OptionView build() {
            return new OptionView(value, label);
        }
    }
}
