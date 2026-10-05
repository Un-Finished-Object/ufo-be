package com.ufo.ufo.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.ufo.ufo.global.config.WebMvcConfig;
import com.ufo.ufo.global.exception.GlobalExceptionHandler;
import com.ufo.ufo.global.security.config.SecurityConfig;
import com.ufo.ufo.global.security.handler.OAuth2SuccessHandler;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.ufo.ufo.global.security.oauth.CustomOAuth2UserService;
import com.ufo.ufo.global.security.oauth.CustomOidcUserService;
import com.ufo.ufo.global.security.resolver.LoginUserArgumentResolver;
import com.ufo.ufo.support.logging.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@SpringBootTest(classes = ObservabilityHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jwt.access-token-expire=60000",
                "spring.jwt.refresh-token-expire=120000",
                "management.server.port=0",
                "management.endpoint.health.group.readiness.include=readinessState"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("요청 식별자와 상태 확인 HTTP 테스트")
class ObservabilityHttpTest {

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private CustomOAuth2UserService oauth2UserService;
    @MockitoBean
    private CustomOidcUserService oidcUserService;
    @MockitoBean
    private OAuth2SuccessHandler successHandler;
    @MockitoBean
    private CookieOAuth2AuthorizationRequestRepository cookieRepository;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrations;
    @MockitoBean
    private LoginUserArgumentResolver loginUserArgumentResolver;

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void closeClient() {
        client.shutdownNow();
    }

    @Test
    @DisplayName("요청 식별자가 없으면 응답에 새 식별자를 반환해야 한다")
    void createsRequestId() throws Exception {
        var response = get(port, "/v1/patterns", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
        assertThat(response.headers().firstValue("X-Request-ID").orElseThrow()).matches("[A-Za-z0-9_-]{1,64}");
    }

    @Test
    @DisplayName("유효한 요청 식별자는 응답에 그대로 반환해야 한다")
    void preservesValidRequestId() throws Exception {
        var response = get(port, "/v1/patterns", "request-123", null);
        assertThat(response.headers().firstValue("X-Request-ID")).contains("request-123");
    }

    @Test
    @DisplayName("허용된 프런트엔드에서 응답의 요청 식별자 헤더를 읽을 수 있어야 한다")
    void exposesRequestIdThroughCors() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/patterns"))
                .header("Origin", "http://localhost:3000").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).contains("X-Request-ID");
    }

    @Test
    @DisplayName("허용하지 않은 문자나 지나치게 긴 요청 식별자는 교체해야 한다")
    void replacesUnsafeRequestId() throws Exception {
        for (String input : new String[] {"user@example.com", "x".repeat(65), "token.part.signature"}) {
            var response = get(port, "/v1/patterns", input, null);
            assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
            assertThat(response.headers().firstValue("X-Request-ID").orElseThrow()).isNotEqualTo(input)
                    .matches("[A-Za-z0-9_-]{1,64}");
        }
    }

    @Test
    @DisplayName("인증이 거부된 요청에도 식별자를 반환하고 기존 접근 제한을 유지해야 한다")
    void includesRequestIdOnAuthenticationFailure() throws Exception {
        var response = get(port, "/v1/users/me", "denied-123", null);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("X-Request-ID")).contains("denied-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"runtime", "servlet", "io"})
    @DisplayName("필터 처리 실패는 요청 식별자를 유지하고 Tomcat 로그에도 예외 원문을 남기지 않아야 한다")
    void hidesFilterFailureFromContainerLogs(String failureType) throws Exception {
        try (var applicationLogs = new LogCapture(RequestIdFilter.class);
                var containerLogs = new LogCapture("org.apache.catalina.core.ContainerBase")) {
            var response = get(port, "/v1/patterns?filterFailure=" + failureType, "failed-123", null);
            assertThat(response.statusCode()).isIn(302, 500);
            assertThat(response.headers().firstValue("X-Request-ID")).contains("failed-123");
            assertThat(response.body()).doesNotContain("exception-secret", "cause-secret", "suppressed-secret");
            assertThat(applicationLogs.events()).hasSize(1);
            assertThat(applicationLogs.events().getFirst().getFormattedMessage()).contains("failed-123");
            var errors = Stream.of(applicationLogs, containerLogs).flatMap(logs -> logs.events().stream())
                    .filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).isNotEmpty().allSatisfy(event -> {
                String trace = event.getThrowableProxy() == null
                        ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy());
                assertThat(event.getFormattedMessage() + trace)
                        .doesNotContain("exception-secret", "cause-secret", "suppressed-secret");
            });
        }
    }

    @ParameterizedTest
    @CsvSource({"400,false", "413,false", "400,true", "413,true"})
    @DisplayName("잘못된 요청 매개변수 오류는 ERROR 로그 없이 상태 코드와 요청 식별자를 유지해야 한다")
    void preservesInvalidParameterHttpStatus(int status, boolean wrapped) throws Exception {
        String failureType = (wrapped ? "wrapped-" : "bad-") + status;
        try (var applicationLogs = new LogCapture(RequestIdFilter.class);
                var containerLogs = new LogCapture("org.apache.catalina.core.ContainerBase")) {
            var response = get(port, "/v1/patterns?filterFailure=" + failureType, "bad-request-123", null);
            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.headers().firstValue("X-Request-ID")).contains("bad-request-123");
            assertThat(response.body()).doesNotContain("exception-secret", "cause-secret", "suppressed-secret");
            var errors = Stream.of(applicationLogs, containerLogs).flatMap(logs -> logs.events().stream())
                    .filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).isEmpty();
        }
    }

    @Test
    @DisplayName("상태 확인은 관리 포트에서만 허용하고 내부 상세 정보를 숨겨야 한다")
    void exposesOnlyHealthOnManagementPort() throws Exception {
        int managementPort = environment.getProperty("local.management.port", Integer.class, port);
        assertThat(managementPort).isNotEqualTo(port);
        for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            var response = get(managementPort, path, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
            var mainResponse = get(port, path, null, tokenProvider.createAccessToken("user@example.com", "ROLE_USER"));
            assertThat(mainResponse.statusCode()).isIn(302, 403, 404);
        }
        assertThat(get(managementPort, "/actuator/env", null, null).statusCode()).isIn(302, 403, 404);
    }

    private HttpResponse<String> get(int targetPort, String path, String requestId, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + targetPort + path)).GET();
        if (requestId != null) {
            request.header("X-Request-ID", requestId);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, RequestIdFilter.class,
            ManagementSecurityConfig.class, PublicController.class})
    static class TestApplication {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 1)
        OncePerRequestFilter failingFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    String failureType = request.getParameter("filterFailure");
                    if (failureType != null) {
                        var cause = new IllegalStateException("cause-secret");
                        if (failureType.startsWith("bad-") || failureType.startsWith("wrapped-")) {
                            int status = failureType.endsWith("413") ? 413 : 400;
                            var failure = new InvalidParameterException(
                                    new IllegalStateException("exception-secret", cause), status);
                            failure.addSuppressed(new IllegalArgumentException("suppressed-secret"));
                            if (failureType.startsWith("wrapped-")) {
                                throw new ServletException("exception-secret", failure);
                            }
                            throw failure;
                        }
                        Exception failure = switch (failureType) {
                            case "servlet" -> new ServletException("exception-secret", cause);
                            case "io" -> new IOException("exception-secret", cause);
                            default -> new IllegalStateException("exception-secret", cause);
                        };
                        failure.addSuppressed(new IllegalArgumentException("suppressed-secret"));
                        switch (failure) {
                            case ServletException exception -> throw exception;
                            case IOException exception -> throw exception;
                            case RuntimeException exception -> throw exception;
                            default -> throw new IllegalStateException(failure);
                        }
                    }
                    chain.doFilter(request, response);
                }
            };
        }

        @Bean
        @Order(-1)
        SecurityFilterChain errorDispatchFilterChain(HttpSecurity http) throws Exception {
            // 컨테이너가 정한 오류 상태를 확인하도록 오류 응답만 인증 검사에서 제외한다.
            return http.securityMatcher("/error")
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }

        @Bean
        JwtTokenProvider tokenProvider() {
            var provider = new JwtTokenProvider("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
            ReflectionTestUtils.setField(provider, "accessTokenExpireTime", 60_000L);
            return provider;
        }
    }

    @RestController
    static class PublicController {

        @GetMapping("/v1/patterns")
        String patterns() {
            return "[]";
        }
    }
}
