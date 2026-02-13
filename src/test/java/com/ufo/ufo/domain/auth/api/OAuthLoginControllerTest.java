package com.ufo.ufo.domain.auth.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth 로그인 인가 API 테스트")
class OAuthLoginControllerTest {

    @Mock
    private OAuthCookieManager oAuthCookieManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OAuthLoginController controller = new OAuthLoginController(oAuthCookieManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Google 인가 요청 시 OAuth2 authorization URI로 302 리다이렉트해야 한다")
    void authorize_ProviderGoogle_RedirectsToOAuthAuthorizationUri() throws Exception {
        String redirectUri = "https://frontend.example.com/auth/callback";
        ResponseCookie cookie = ResponseCookie.from("oauth_redirect_uri", "encoded").build();
        when(oAuthCookieManager.createRedirectUriCookie(eq(redirectUri), eq(false))).thenReturn(cookie);

        mockMvc.perform(get("/v1/auth/login/google/authorize")
                        .queryParam("redirect_uri", redirectUri))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/oauth2/authorization/google"))
                .andExpect(header().string("Set-Cookie", cookie.toString()));
    }
}
