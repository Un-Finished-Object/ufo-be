package com.ufo.ufo.global.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "prod", "test"})
@RequiredArgsConstructor
public class RequiredEnvironmentValidator {

    private static final List<String> REQUIRED_VARIABLES = List.of(
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "JWT_SECRET",
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "KAKAO_CLIENT_ID",
            "KAKAO_CLIENT_SECRET",
            "NAVER_CLIENT_ID",
            "NAVER_CLIENT_SECRET",
            "REFERRAL_HMAC_SECRET",
            "S3_IMAGE_BUCKET",
            "S3_PUBLIC_BASE_URL",
            "CDN_BASE_URL",
            "OAUTH_REDIRECT_URL",
            "OAUTH_SIGNUP_REDIRECT_URL",
            "OAUTH_ADMIN_REDIRECT_URL"
    );

    private final Environment environment;

    @PostConstruct
    public void validate() {
        for (String name : REQUIRED_VARIABLES) {
            String value = environment.getProperty(name);
            boolean emptyTestPassword = name.equals("DB_PASSWORD")
                    && environment.acceptsProfiles(Profiles.of("test")) && value != null;
            if (!emptyTestPassword && (value == null || value.isBlank())) {
                throw new IllegalStateException("필수 환경 변수가 없습니다: " + name);
            }
        }
    }
}
