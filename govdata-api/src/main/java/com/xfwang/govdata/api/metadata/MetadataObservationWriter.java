package com.xfwang.govdata.api.metadata;

import java.util.List;

/**
 * 采集模块写入可信当前元数据的稳定契约。
 *
 * <p>实现必须使用有界短事务和批量持久化；完整运行结束时才允许执行缺失检测。</p>
 */
public interface MetadataObservationWriter {

    MetadataBatchWriteResult writeNamespaces(MetadataObservationContext context,
                                              List<CollectedMetadataNamespace> namespaces);

    MetadataBatchWriteResult writeTables(MetadataObservationContext context,
                                          List<CollectedMetadataTable> tables);

    /**
     * 完成一次采集运行。
     *
     * @param complete 发现和写入是否完整成功
     * @param authoritativeFullScope 是否覆盖数据源全部对象；仅该条件成立时允许缺失检测
     */
    MetadataRunCompletionResult completeRun(MetadataObservationContext context, boolean complete,
                                              boolean authoritativeFullScope);
}
