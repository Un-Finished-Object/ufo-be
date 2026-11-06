package com.ufo.ufo.domain.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ChatEventOutboxExecutionConfig {

    @Bean
    public ThreadPoolTaskExecutor chatEventOutboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-outbox-");
        return executor;
    }
}
