package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;

/** Connector 页面描述能力。 */
public interface ConnectorDescriptorProvider {
    ConnectorDescriptor descriptor();
}
