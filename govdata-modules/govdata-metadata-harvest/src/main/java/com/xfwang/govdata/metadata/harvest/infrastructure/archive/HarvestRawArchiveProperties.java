package com.xfwang.govdata.metadata.harvest.infrastructure.archive;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 原始采集结果归档配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "govdata.metadata.harvest.raw-archive")
public class HarvestRawArchiveProperties {
    private boolean enabled;
    @Min(1)
    @Max(3650)
    private int retentionDays = 30;
    @Min(1024)
    @Max(67_108_864)
    private long maxBytes = 10 * 1024 * 1024;
    @Min(1)
    @Max(500)
    private int cleanupBatchSize = 100;
}
