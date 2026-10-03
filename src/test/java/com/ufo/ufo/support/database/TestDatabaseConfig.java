package com.ufo.ufo.support.database;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "ufo.test.database", havingValue = "mysql", matchIfMissing = true)
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("ufo_test")
                .withUsername("ufo_test")
                .withPassword("test")
                .withEnv("TZ", "Asia/Seoul")
                .withUrlParam("serverTimezone", "Asia/Seoul")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                        "--default-time-zone=+09:00", "--innodb-lock-wait-timeout=10");
    }

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "ufo.test.database", havingValue = "postgres")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17")
                .withDatabaseName("ufo_test")
                .withUsername("ufo_test")
                .withPassword("test")
                .withEnv("TZ", "Asia/Seoul")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withCommand("postgres", "-c", "timezone=Asia/Seoul", "-c", "lock_timeout=10s");
    }
}
