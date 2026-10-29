package com.xfwang.govdata.metadata.harvest.application.archive;

/** 原始采集结果对象存储与引用持久化 Adapter seam。 */
public interface HarvestRawArchivePort {
    void archive(Long harvestRunId, int batchNo, Object batch);
    int cleanupExpired();
}
