package com.ufo.ufo.domain.referral;

import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.referral")
public record ReferralCodeProperties(String hmacSecret) {

    private static final int MIN_SECRET_LENGTH = 32;

    public String requiredHmacSecret() {
        if (hmacSecret == null || hmacSecret.length() < MIN_SECRET_LENGTH) {
            throw new ReferralCodeGenerationException();
        }
        return hmacSecret;
    }
}
