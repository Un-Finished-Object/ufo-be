package com.ufo.ufo.global.security.handler;

import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.CustomOAuth2User;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthCookieManager oAuthCookieManager;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getEmail();
        String role = oAuth2User.getRoleKey();

        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        log.info("Social Login Success: email={}, role={}", email, role);

        String targetUrl = oAuthCookieManager.extractRedirectUri(request);

        response.addHeader(HttpHeaders.SET_COOKIE,
                oAuthCookieManager.createRefreshTokenCookie(
                        refreshToken,
                        jwtTokenProvider.getRefreshTokenExpireTime() / 1000,
                        request.isSecure()
                ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                oAuthCookieManager.expireRedirectUriCookie(request.isSecure()).toString());

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
