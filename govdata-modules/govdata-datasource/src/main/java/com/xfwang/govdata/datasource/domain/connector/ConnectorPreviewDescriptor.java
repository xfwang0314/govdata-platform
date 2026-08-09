package com.xfwang.govdata.datasource.domain.connector;

/** Connector 在页面展示的非敏感连接目标预览。 */
public class ConnectorPreviewDescriptor {
    private final String label;
    private final String template;

    public ConnectorPreviewDescriptor(String label, String template) {
        this.label = label;
        this.template = template;
    }

    public String getLabel() { return label; }
    public String getTemplate() { return template; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String label;
        private String template;

        public Builder label(String label) { this.label = label; return this; }
        public Builder template(String template) { this.template = template; return this; }

        public ConnectorPreviewDescriptor build() {
            return new ConnectorPreviewDescriptor(label, template);
        }
    }
}
