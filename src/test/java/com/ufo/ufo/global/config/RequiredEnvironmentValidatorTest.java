package com.ufo.ufo.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("필수 환경 변수 검증")
class RequiredEnvironmentValidatorTest {

    @Test
    @DisplayName("JWT Secret이 없으면 변수 이름을 알려 주고 시작을 중단한다")
    void missingJwtSecretFails() {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("JWT_SECRET", "");

        assertThatThrownBy(() -> new RequiredEnvironmentValidator().validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("OAuth Client Secret이 비어 있으면 시작을 중단한다")
    void blankOauthSecretFails() {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("NAVER_CLIENT_SECRET", " ");

        assertThatThrownBy(() -> new RequiredEnvironmentValidator().validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NAVER_CLIENT_SECRET");
    }

    @Test
    @DisplayName("S3 버킷이 없으면 시작을 중단한다")
    void missingS3BucketFails() {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("S3_IMAGE_BUCKET", "");

        assertThatThrownBy(() -> new RequiredEnvironmentValidator().validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_IMAGE_BUCKET");
    }

    @Test
    @DisplayName("테스트 프로필의 빈 H2 비밀번호는 허용한다")
    void testProfileAllowsEmptyDatabasePassword() {
        MockEnvironment environment = completeEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("DB_PASSWORD", "");

        assertThatCode(() -> new RequiredEnvironmentValidator().validate(environment)).doesNotThrowAnyException();
    }

    private MockEnvironment completeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        for (String name : new String[] {
                "DB_URL", "DB_USERNAME", "DB_PASSWORD",
                "JWT_SECRET", "REFERRAL_HMAC_SECRET", "S3_IMAGE_BUCKET", "S3_PUBLIC_BASE_URL",
                "CDN_BASE_URL", "OAUTH_REDIRECT_URL", "OAUTH_SIGNUP_REDIRECT_URL", "OAUTH_ADMIN_REDIRECT_URL"
        }) {
            environment.setProperty(name, "configured");
        }
        for (String provider : new String[] {"GOOGLE", "KAKAO", "NAVER"}) {
            String prefix = provider;
            environment.setProperty(prefix + "_CLIENT_ID", "configured");
            environment.setProperty(prefix + "_CLIENT_SECRET", "configured");
        }
        return environment;
    }
}
