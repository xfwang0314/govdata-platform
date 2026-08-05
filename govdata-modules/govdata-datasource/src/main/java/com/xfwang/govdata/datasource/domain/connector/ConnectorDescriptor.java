package com.xfwang.govdata.datasource.domain.connector;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/** Connector 的稳定页面描述和真实能力声明。 */
@Value
@Builder
public class ConnectorDescriptor {
    String connectorCode;
    String connectorName;
    String connectorVersion;
    String category;
    String secretType;
    @Builder.Default
    Set<String> capabilities = Set.of();
    @Builder.Default
    List<ConnectorFieldDescriptor> endpointFields = List.of();
    @Builder.Default
    List<String> secretKeys = List.of();
}
