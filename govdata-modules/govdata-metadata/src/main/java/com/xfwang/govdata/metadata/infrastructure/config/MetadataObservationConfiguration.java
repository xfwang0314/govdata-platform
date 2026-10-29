package com.xfwang.govdata.metadata.infrastructure.config;

import com.xfwang.govdata.metadata.application.observation.MetadataMissingGuardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 元数据观察配置。 */
@Configuration
@EnableConfigurationProperties(MetadataMissingGuardProperties.class)
public class MetadataObservationConfiguration {
}
