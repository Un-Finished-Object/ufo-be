package com.ufo.ufo.domain.image.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.image.tasks.enabled", havingValue = "true", matchIfMissing = true)
public class ProfileImageTaskSchedulingConfig {
}
