package com.ufo.ufo.domain.auth.api;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.domain.auth.application.AuthService;
import com.ufo.ufo.domain.auth.dto.request.SignupRequest;
import com.ufo.ufo.domain.auth.dto.response.SignupResponse;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth 컨트롤러 테스트")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private OAuthCookieManager oAuthCookieManager;

    private MockMvc mockMvc;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, oAuthCookieManager);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("회원가입 API는 사용자 정보와 관심사를 data에 담아 반환해야 한다")
    void signup_ReturnsSignupResponse() {
        User user = UserFixture.createUserWithId(10L);
        SignupRequest request = new SignupRequest(
                "user01",
                "profiles/10/profile.png",
                List.of("빈티지", "캐주얼")
        );
        SignupResponse serviceResponse = new SignupResponse(
                10L,
                "user01",
                "https://cdn.example.com/profiles/10/profile.png",
                List.of("빈티지", "캐주얼")
        );
        when(authService.signup(user, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<SignupResponse>> response = authController.signup(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().error()).isNull();
        verify(authService).signup(user, request);
    }

    @Test
    @DisplayName("토큰 재발급 API는 성공 시 data에 토큰 정보를 반환해야 한다")
    void refreshToken_ReturnsApiResponse() throws Exception {
        when(authService.reissue("refresh-token"))
                .thenReturn(new TokenResponse("access-token", "Bearer", 1000L));

        mockMvc.perform(post("/v1/auth/token/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1000))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    @DisplayName("로그아웃 API는 refresh_token 만료 쿠키를 내려주고 빈 data를 반환해야 한다")
    void logout_ExpiresRefreshTokenCookie() throws Exception {
        ResponseCookie expiredCookie = ResponseCookie.from("refresh_token", "")
                .maxAge(0)
                .path("/")
                .build();
        when(oAuthCookieManager.expireRefreshTokenCookie(anyBoolean())).thenReturn(expiredCookie);

        mockMvc.perform(post("/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.error").value(nullValue()));

        verify(authService).logout(org.mockito.ArgumentMatchers.any(HttpServletRequest.class));
    }
}
