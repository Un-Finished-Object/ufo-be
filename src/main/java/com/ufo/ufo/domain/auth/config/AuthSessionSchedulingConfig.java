package com.ufo.ufo.domain.auth.config;

import com.ufo.ufo.domain.auth.application.AuthSessionTaskRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.auth.sessions.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Import(AuthSessionTaskRunner.class)
public class AuthSessionSchedulingConfig {
}
