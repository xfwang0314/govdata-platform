package com.xfwang.govdata.datasource.infrastructure.connector.support;

/**
 * Connector 专属非敏感端点校验扩展缝。
 */
public interface ConnectorEndpointValidator {
    boolean supports(String connectorCode, String connectorVersion);

    void validate(ConnectorEndpointValidationContext context);
}
