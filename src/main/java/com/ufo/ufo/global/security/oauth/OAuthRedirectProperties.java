package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.global.exception.OAuthCookieDomainNotConfiguredException;
import com.ufo.ufo.global.exception.OAuthRedirectUrlNotConfiguredException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthRedirectProperties(
        String redirectUrl,
        String signupRedirectUrl,
        String adminPage,
        String cookieDomain
) {

    public String requiredRedirectUrl() {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new OAuthRedirectUrlNotConfiguredException();
        }
        return redirectUrl;
    }

    public String requiredCookieDomain() {
        if (cookieDomain == null || cookieDomain.isBlank()) {
            throw new OAuthCookieDomainNotConfiguredException();
        }
        return cookieDomain;
    }

    public String requiredSignupRedirectUrl() {
        if (signupRedirectUrl == null || signupRedirectUrl.isBlank()) {
            throw new OAuthRedirectUrlNotConfiguredException();
        }
        return signupRedirectUrl;
    }

    public String requiredAdminRedirectUrl() {
        if (adminPage == null || adminPage.isBlank()) {
            throw new OAuthRedirectUrlNotConfiguredException();
        }
        String page = adminPage.startsWith("/") ? adminPage.substring(1) : adminPage;
        return "/admin/" + page;
    }
}
