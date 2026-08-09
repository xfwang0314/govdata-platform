package com.xfwang.govdata.datasource.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 数据源连接测试和元数据探测的线程池配置。
 */
@Configuration
public class ConnectionTestExecutorConfig {

    /**
     * 配置连接测试执行器
     *
     * @return 线程池执行器
     */
    @Bean("connectionTestExecutor")
    public ThreadPoolTaskExecutor connectionTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ds-test-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
