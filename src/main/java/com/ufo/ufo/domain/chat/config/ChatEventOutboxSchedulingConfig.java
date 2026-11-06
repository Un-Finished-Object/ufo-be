package com.ufo.ufo.domain.chat.config;

import com.ufo.ufo.domain.chat.application.ChatEventOutboxTaskRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.chat.outbox.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Import(ChatEventOutboxTaskRunner.class)
public class ChatEventOutboxSchedulingConfig {
}
