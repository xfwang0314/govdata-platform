package com.xfwang.govdata.metadata.harvest.infrastructure.scheduling.quartz;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/** Quartz Job 依赖注入配置。 */
@Configuration
@ConditionalOnProperty(name = "govdata.metadata.harvest.scheduler.provider", havingValue = "quartz",
    matchIfMissing = true)
public class QuartzHarvestConfiguration {

    @Bean
    JobFactory harvestQuartzJobFactory(AutowireCapableBeanFactory beanFactory) {
        return new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        };
    }

    @Bean
    SchedulerFactoryBeanCustomizer harvestQuartzCustomizer(JobFactory harvestQuartzJobFactory) {
        return factory -> factory.setJobFactory(harvestQuartzJobFactory);
    }
}
