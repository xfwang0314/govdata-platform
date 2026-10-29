package com.xfwang.govdata.api.metadata;

/** 数据源模块向元数据采集模块开放的受控发现 seam。 */
public interface MetadataDiscoveryGateway {

    MetadataDiscoveryResult discover(MetadataDiscoveryRequest request, MetadataDiscoverySink sink);
}
