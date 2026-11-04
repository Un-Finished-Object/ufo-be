package com.ufo.ufo.domain.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.auth.sessions.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionSchedulingConfig {
}
