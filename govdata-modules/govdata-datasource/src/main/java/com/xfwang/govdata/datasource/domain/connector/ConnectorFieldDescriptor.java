package com.xfwang.govdata.datasource.domain.connector;

import java.util.List;
import java.util.Set;

/**
 * 页面生成 Connector 非敏感配置表单所需的字段描述。
 */
public record ConnectorFieldDescriptor(String code, String label, String valueType, boolean required,
                                       Object defaultValue, Integer minValue, Integer maxValue, List<String> options,
                                       Set<String> authProfileCodes, String description) {
    public ConnectorFieldDescriptor(String code, String label, String valueType, boolean required,
                                    Object defaultValue, Integer minValue, Integer maxValue,
                                    List<String> options, Set<String> authProfileCodes, String description) {
        this.code = code;
        this.label = label;
        this.valueType = valueType;
        this.required = required;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.options = options == null ? List.of() : options;
        this.authProfileCodes = authProfileCodes == null ? Set.of() : authProfileCodes;
        this.description = description;
    }

    public boolean appliesTo(String authProfileCode) {
        return authProfileCodes.isEmpty() || authProfileCodes.contains(authProfileCode);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String code;
        private String label;
        private String valueType;
        private boolean required;
        private Object defaultValue;
        private Integer minValue;
        private Integer maxValue;
        private List<String> options = List.of();
        private Set<String> authProfileCodes = Set.of();
        private String description;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder valueType(String valueType) {
            this.valueType = valueType;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder minValue(Integer minValue) {
            this.minValue = minValue;
            return this;
        }

        public Builder maxValue(Integer maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        public Builder options(List<String> options) {
            this.options = options;
            return this;
        }

        public Builder authProfileCodes(Set<String> authProfileCodes) {
            this.authProfileCodes = authProfileCodes;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ConnectorFieldDescriptor build() {
            return new ConnectorFieldDescriptor(code, label, valueType, required, defaultValue,
                minValue, maxValue, options, authProfileCodes, description);
        }
    }
}
