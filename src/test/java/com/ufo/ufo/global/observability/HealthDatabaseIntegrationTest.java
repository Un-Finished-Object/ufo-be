package com.ufo.ufo.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.ufo.ufo.support.database.TestDatabaseConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = HealthDatabaseIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=application,application-db-test", "management.server.port=0"})
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 상태 확인 통합 테스트")
class HealthDatabaseIntegrationTest {

    @Autowired
    private Environment environment;
    @MockitoSpyBean
    private DataSource dataSource;

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void closeClient() {
        client.shutdownNow();
    }

    @Test
    @DisplayName("실제 DB에 연결되면 실행 상태와 준비 상태가 정상이어야 한다")
    void acceptsRequestsWhenDatabaseIsAvailable() throws Exception {
        try (var connection = dataSource.getConnection()) {
            String selected = System.getProperty("ufo.test.database", "mysql");
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo(selected.equals("mysql") ? "MySQL" : "PostgreSQL");
        }
        assertHealthy("liveness");
        assertHealthy("readiness");
    }

    @Test
    @DisplayName("DB 연결 실패는 준비 상태를 실패로 바꾸되 실행 상태에는 영향을 주지 않아야 한다")
    void databaseFailureOnlyAffectsReadiness() throws Exception {
        doThrow(new SQLException("테스트 DB 연결 실패")).when(dataSource).getConnection();
        var response = get("/actuator/health/readiness");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEqualTo("{\"status\":\"DOWN\"}");
        assertHealthy("liveness");
    }

    @Test
    @DisplayName("S3 상태가 실패해도 실행 상태와 DB 준비 상태에는 영향을 주지 않아야 한다")
    void storageFailureDoesNotRejectTraffic() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(503);
        assertHealthy("liveness");
        assertHealthy("readiness");
    }

    private void assertHealthy(String group) throws Exception {
        var response = get("/actuator/health/" + group);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    private HttpResponse<String> get(String path) throws Exception {
        int port = environment.getRequiredProperty("local.management.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class, OAuth2ClientAutoConfiguration.class})
    @Import({TestDatabaseConfig.class, ManagementSecurityConfig.class, RequestIdFilter.class})
    static class TestApplication {

        @Bean
        HealthIndicator s3HealthIndicator() {
            return () -> Health.down().withDetail("bucket", "비공개 버킷").build();
        }

        @Bean
        SecurityFilterChain applicationFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
        }
    }
}
