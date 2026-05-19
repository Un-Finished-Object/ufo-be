package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.global.exception.OAuthRedirectUrlNotConfiguredException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthRedirectProperties(
        String redirectUrl
) {

    public String requiredRedirectUrl() {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new OAuthRedirectUrlNotConfiguredException();
        }
        return redirectUrl;
    }
}
