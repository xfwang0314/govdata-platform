package com.xfwang.govdata.datasource.domain.connector;

import java.util.List;
import java.util.Set;

/**
 * Connector 的稳定页面描述和真实能力声明。
 */
public class ConnectorDescriptor {
    private final String connectorCode;
    private final String connectorName;
    private final String connectorVersion;
    private final String category;
    private final ConnectorPreviewDescriptor connectionPreview;
    private final boolean customPropertiesSupported;
    private final Set<String> capabilities;
    private final List<ConnectorFieldDescriptor> endpointFields;
    private final List<ConnectorAuthProfile> authProfiles;

    public ConnectorDescriptor(String connectorCode, String connectorName, String connectorVersion, String category,
                               ConnectorPreviewDescriptor connectionPreview, boolean customPropertiesSupported,
                               Set<String> capabilities, List<ConnectorFieldDescriptor> endpointFields,
                               List<ConnectorAuthProfile> authProfiles) {
        this.connectorCode = connectorCode;
        this.connectorName = connectorName;
        this.connectorVersion = connectorVersion;
        this.category = category;
        this.connectionPreview = connectionPreview;
        this.customPropertiesSupported = customPropertiesSupported;
        this.capabilities = capabilities == null ? Set.of() : capabilities;
        this.endpointFields = endpointFields == null ? List.of() : endpointFields;
        this.authProfiles = authProfiles == null ? List.of() : authProfiles;
    }

    public String getConnectorCode() {
        return connectorCode;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getConnectorVersion() {
        return connectorVersion;
    }

    public String getCategory() {
        return category;
    }

    public ConnectorPreviewDescriptor getConnectionPreview() {
        return connectionPreview;
    }

    public boolean isCustomPropertiesSupported() {
        return customPropertiesSupported;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public List<ConnectorFieldDescriptor> getEndpointFields() {
        return endpointFields;
    }

    public List<ConnectorAuthProfile> getAuthProfiles() {
        return authProfiles;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String connectorCode;
        private String connectorName;
        private String connectorVersion;
        private String category;
        private ConnectorPreviewDescriptor connectionPreview;
        private boolean customPropertiesSupported;
        private Set<String> capabilities = Set.of();
        private List<ConnectorFieldDescriptor> endpointFields = List.of();
        private List<ConnectorAuthProfile> authProfiles = List.of();

        public Builder connectorCode(String connectorCode) {
            this.connectorCode = connectorCode;
            return this;
        }

        public Builder connectorName(String connectorName) {
            this.connectorName = connectorName;
            return this;
        }

        public Builder connectorVersion(String connectorVersion) {
            this.connectorVersion = connectorVersion;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder connectionPreview(ConnectorPreviewDescriptor connectionPreview) {
            this.connectionPreview = connectionPreview;
            return this;
        }

        public Builder customPropertiesSupported(boolean customPropertiesSupported) {
            this.customPropertiesSupported = customPropertiesSupported;
            return this;
        }

        public Builder capabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder endpointFields(List<ConnectorFieldDescriptor> endpointFields) {
            this.endpointFields = endpointFields;
            return this;
        }

        public Builder authProfiles(List<ConnectorAuthProfile> authProfiles) {
            this.authProfiles = authProfiles;
            return this;
        }

        public ConnectorDescriptor build() {
            return new ConnectorDescriptor(connectorCode, connectorName, connectorVersion, category,
                connectionPreview, customPropertiesSupported, capabilities, endpointFields, authProfiles);
        }
    }
}
