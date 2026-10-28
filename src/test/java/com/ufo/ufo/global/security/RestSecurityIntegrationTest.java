package com.ufo.ufo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.global.config.JacksonConfig;
import com.ufo.ufo.global.security.config.SecurityConfig;
import com.ufo.ufo.global.security.handler.OAuth2SuccessHandler;
import com.ufo.ufo.global.security.handler.RestSecurityExceptionHandler;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.ufo.ufo.global.security.oauth.CustomOAuth2UserService;
import com.ufo.ufo.global.security.oauth.CustomOidcUserService;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = {
        "spring.jwt.secret=" + RestSecurityIntegrationTest.JWT_SECRET,
        "spring.jwt.access-token-expire=60000",
        "spring.jwt.refresh-token-expire=120000"
})
@ContextConfiguration(classes = RestSecurityIntegrationTest.TestApplication.class)
@DisplayName("REST 인증과 권한 오류 응답 통합 테스트")
class RestSecurityIntegrationTest {

    static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Autowired
    private MockMvc mockMvc;

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

    @ParameterizedTest
    @CsvSource({
            "missing,application/json", "missing,text/html",
            "malformed,application/json", "malformed,text/html",
            "expired,application/json", "expired,text/html",
            "wrong-signature,application/json", "wrong-signature,text/html",
            "tampered,application/json", "tampered,text/html",
            "refresh,application/json", "refresh,text/html"
    })
    @DisplayName("토큰 인증에 실패하면 Accept 헤더와 관계없이 리다이렉트 없이 401 JSON을 반환해야 한다")
    void request_WithoutValidAccessToken_ReturnsUnauthorizedJson(String tokenKind, String accept) throws Exception {
        String token = invalidToken(tokenKind);
        MockHttpServletRequestBuilder request = get("/v1/users/me").accept(accept);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        String body = mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("401"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("user@example.com", "Exception", "signature", JWT_SECRET);
        if (token != null) {
            assertThat(body).doesNotContain(token);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html"})
    @DisplayName("일반 사용자가 관리자 API를 요청하면 리다이렉트 없이 403 JSON을 반환해야 한다")
    void request_WithInsufficientAuthority_ReturnsForbiddenJson(String accept) throws Exception {
        mockMvc.perform(get("/v1/admin/chats").accept(accept)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenProvider.createAccessToken("user@example.com", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("403"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_GUEST", "ROLE_USER", "ROLE_ADMIN"})
    @DisplayName("정상 Access Token의 사용자는 보호된 API에 접근할 수 있어야 한다")
    void request_WithValidAccessToken_AuthenticatesUser(String role) throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenProvider.createAccessToken("user@example.com", role)))
                .andExpect(status().isOk())
                .andExpect(content().string("user@example.com"));
    }

    @Test
    @DisplayName("관리자 Access Token은 관리자 API에 접근할 수 있어야 한다")
    void request_WithAdminAccessToken_AllowsAdminApi() throws Exception {
        mockMvc.perform(get("/v1/admin/chats")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenProvider.createAccessToken("admin@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin@example.com"));
    }

    @Test
    @DisplayName("공개 도안 API는 토큰 없이도 접근할 수 있어야 한다")
    void request_PublicApiWithoutToken_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/v1/patterns"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    @DisplayName("OAuth 로그인 진입은 공급자 인증 화면으로 리다이렉트해야 한다")
    void login_WithOAuthProvider_RedirectsToAuthorizationPage() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/google").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location).startsWith("https://accounts.example.test/oauth/authorize?");
    }

    private String invalidToken(String kind) {
        return switch (kind) {
            case "missing" -> null;
            case "malformed" -> "not-a-token";
            case "expired" -> {
                JwtTokenProvider expired = new JwtTokenProvider(JWT_SECRET);
                ReflectionTestUtils.setField(expired, "accessTokenExpireTime", -60_000L);
                yield expired.createAccessToken("user@example.com", "ROLE_USER");
            }
            case "wrong-signature" -> {
                String otherSecret = Base64.getEncoder().encodeToString(
                        "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
                JwtTokenProvider other = new JwtTokenProvider(otherSecret);
                ReflectionTestUtils.setField(other, "accessTokenExpireTime", 60_000L);
                yield other.createAccessToken("user@example.com", "ROLE_USER");
            }
            case "tampered" -> {
                String token = tokenProvider.createAccessToken("user@example.com", "ROLE_USER");
                int signatureStart = token.lastIndexOf('.') + 1;
                char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
                yield token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
            }
            case "refresh" -> tokenProvider.createRefreshToken("user@example.com");
            default -> throw new IllegalArgumentException("알 수 없는 테스트 토큰 종류");
        };
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SecurityConfig.class, JwtTokenProvider.class, JacksonConfig.class,
            RestSecurityExceptionHandler.class, TestController.class})
    static class TestApplication {

        @Bean
        ClientRegistrationRepository clientRegistrations() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/v1/auth/oauth/google/callback")
                    .authorizationUri("https://accounts.example.test/oauth/authorize")
                    .tokenUri("https://accounts.example.test/oauth/token")
                    .userInfoUri("https://accounts.example.test/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("Google")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }

    @RestController
    static class TestController {

        @GetMapping({"/v1/users/me", "/v1/admin/chats"})
        String currentUser(Principal principal) {
            return principal.getName();
        }

        @GetMapping("/v1/patterns")
        String patterns() {
            return "[]";
        }
    }
}
