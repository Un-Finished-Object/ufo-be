package com.ufo.ufo.global.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("REST JWT 인증 필터 테스트")
class JwtAuthenticationFilterTest {

    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        provider = new JwtTokenProvider(secret);
        ReflectionTestUtils.setField(provider, "accessTokenExpireTime", 60_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpireTime", 120_000L);
        filter = new JwtAuthenticationFilter(provider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Refresh Token을 Bearer 헤더로 보내도 REST 사용자를 인증하지 않아야 한다")
    void refreshToken_DoesNotAuthenticateRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.createRefreshToken("user@example.com"));

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test
    @DisplayName("Access Token을 Bearer 헤더로 보내면 REST 사용자를 인증해야 한다")
    void accessToken_AuthenticatesRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION,
                "Bearer " + provider.createAccessToken("user@example.com", "ROLE_USER"));

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("user@example.com");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_USER");
        });
    }
}
