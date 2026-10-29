package com.xfwang.govdata.metadata.harvest.interfaces.scheduler;

import com.xfwang.govdata.metadata.harvest.application.archive.HarvestRawArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 原始采集结果保留期清理入口。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarvestRawResultCleanupScheduler {
    private final HarvestRawArchiveService archiveService;

    @Scheduled(cron = "${govdata.metadata.harvest.raw-archive.cleanup-cron:0 20 3 * * *}")
    public void cleanupExpired() {
        int deleted = archiveService.cleanupExpired();
        if (deleted > 0) log.info("元数据采集原始归档清理完成 deletedCount={}", deleted);
    }
}
