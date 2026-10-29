package com.xfwang.govdata.api.metadata;

import java.util.List;

/**
 * 数据源发现过程的有界批次接收器。
 *
 * <p>命名空间必须先于引用它的表提交；每个表批次中的表均携带完整字段集合。</p>
 */
public interface MetadataDiscoverySink {

    void acceptNamespaces(List<CollectedMetadataNamespace> namespaces);

    void acceptTables(List<CollectedMetadataTable> tables);
}
