package com.ufo.ufo.global.config;

import java.util.List;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

public class RequiredEnvironmentValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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
            "OAUTH_ADMIN_REDIRECT_URL",
            "OAUTH_COOKIE_DOMAIN"
    );

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (environment.acceptsProfiles(Profiles.of("dev", "prod", "test"))) {
            validate(environment);
        }
    }

    public void validate(Environment environment) {
        boolean testOnly = environment.acceptsProfiles(Profiles.of("test"))
                && !environment.acceptsProfiles(Profiles.of("dev", "prod"));
        for (String name : REQUIRED_VARIABLES) {
            String value = environment.getProperty(name);
            boolean emptyTestPassword = name.equals("DB_PASSWORD")
                    && testOnly && "".equals(value);
            if (!emptyTestPassword && (value == null || value.isBlank())) {
                throw new IllegalStateException("필수 환경 변수가 없습니다: " + name);
            }
        }
    }
}
