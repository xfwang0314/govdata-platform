package com.xfwang.govdata.metadata.harvest.application.archive;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 原始采集结果归档与保留期清理用例。 */
@Service
@RequiredArgsConstructor
public class HarvestRawArchiveService {
    private final HarvestRawArchivePort archivePort;

    public void archive(Long harvestRunId, int batchNo, Object batch) {
        archivePort.archive(harvestRunId, batchNo, batch);
    }

    public int cleanupExpired() {
        return archivePort.cleanupExpired();
    }
}
