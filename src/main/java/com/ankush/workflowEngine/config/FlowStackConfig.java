package com.ankush.workflowEngine.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableCaching
@EnableConfigurationProperties({OllamaProperties.class, OpenAiProperties.class})
public class FlowStackConfig {

    @Bean(name = "workflowAsyncExecutor")
    public Executor workflowAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("flowstack-exec-");
        executor.initialize();
        return executor;
    }
}
