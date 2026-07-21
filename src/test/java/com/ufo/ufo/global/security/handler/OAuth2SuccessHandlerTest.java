package com.ufo.ufo.global.security.handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.CustomOAuth2User;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import com.ufo.ufo.global.security.oauth.OAuthRedirectProperties;
import com.ufo.ufo.global.security.types.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.RedirectStrategy;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth 로그인 성공 핸들러 테스트")
class OAuth2SuccessHandlerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthCookieManager oAuthCookieManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private RedirectStrategy redirectStrategy;

    private OAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        OAuthRedirectProperties properties = new OAuthRedirectProperties(
                "https://ufo.example.com",
                "https://ufo.example.com/auth/signup",
                ".ufo.example.com"
        );
        successHandler = new OAuth2SuccessHandler(jwtTokenProvider, oAuthCookieManager, properties);
        successHandler.setRedirectStrategy(redirectStrategy);
    }

    @Test
    @DisplayName("게스트 사용자는 회원가입 페이지로 이동해야 한다")
    void onAuthenticationSuccess_WithGuest_RedirectsToSignup() throws Exception {
        stubAuthentication(Role.ROLE_GUEST);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(request, response, "https://ufo.example.com/auth/signup");
    }

    @Test
    @DisplayName("가입 완료 사용자는 기본 페이지로 이동해야 한다")
    void onAuthenticationSuccess_WithUser_RedirectsToDefault() throws Exception {
        stubAuthentication(Role.ROLE_USER);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(redirectStrategy).sendRedirect(request, response, "https://ufo.example.com");
    }

    private void stubAuthentication(Role role) {
        CustomOAuth2User oAuth2User = new CustomOAuth2User(
                "test@example.com",
                role.name(),
                Map.of("email", "test@example.com")
        );
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(jwtTokenProvider.createRefreshToken("test@example.com")).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpireTime()).thenReturn(3_600_000L);
        when(oAuthCookieManager.createRefreshTokenCookie("refresh-token", 3_600L, false))
                .thenReturn(ResponseCookie.from("refresh_token", "refresh-token").build());
    }
}
