package com.xfwang.govdata.datasource.domain.connector;

import com.xfwang.platform.api.secret.SecretType;

import java.util.Set;

/**
 * Connector 支持的认证方式及其凭据契约。
 */
public class ConnectorAuthProfile {
    private final String profileCode;
    private final String profileName;
    private final SecretType secretType;
    private final Set<String> requiredKeys;

    public ConnectorAuthProfile(String profileCode, String profileName, SecretType secretType, Set<String> requiredKeys) {
        this.profileCode = profileCode;
        this.profileName = profileName;
        this.secretType = secretType;
        this.requiredKeys = requiredKeys == null ? Set.of() : requiredKeys;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public String getProfileName() {
        return profileName;
    }

    public SecretType getSecretType() {
        return secretType;
    }

    public Set<String> getRequiredKeys() {
        return requiredKeys.isEmpty() && secretType != null ? secretType.requiredKeys() : requiredKeys;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String profileCode;
        private String profileName;
        private SecretType secretType;
        private Set<String> requiredKeys = Set.of();

        public Builder profileCode(String profileCode) {
            this.profileCode = profileCode;
            return this;
        }

        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        public Builder secretType(SecretType secretType) {
            this.secretType = secretType;
            return this;
        }

        public Builder requiredKeys(Set<String> requiredKeys) {
            this.requiredKeys = requiredKeys;
            return this;
        }

        public ConnectorAuthProfile build() {
            return new ConnectorAuthProfile(profileCode, profileName, secretType, requiredKeys);
        }
    }
}
