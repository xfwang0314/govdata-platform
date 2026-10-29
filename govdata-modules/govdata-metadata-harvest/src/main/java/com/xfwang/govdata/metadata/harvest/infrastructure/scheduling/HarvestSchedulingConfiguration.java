package com.xfwang.govdata.metadata.harvest.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用归档清理等本地定时入口；采集计划触发由可替换调度 Adapter 承担。
 */
@Configuration
@EnableScheduling
public class HarvestSchedulingConfiguration {
}
