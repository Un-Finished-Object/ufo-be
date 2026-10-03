package com.ufo.ufo.support.database;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@TestConfiguration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingTestConfig {
}
