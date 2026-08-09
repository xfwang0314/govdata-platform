package com.xfwang.govdata.datasource.infrastructure.connector.support;

import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;

import java.util.Map;

/**
 * Connector 专属端点校验上下文。
 */
public record ConnectorEndpointValidationContext(ConnectorDescriptor descriptor, String authProfileCode,
                                                 String environment, Map<String, Object> endpointConfig) {
}
