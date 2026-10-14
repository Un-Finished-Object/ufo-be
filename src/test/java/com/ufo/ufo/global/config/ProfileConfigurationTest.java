package com.ufo.ufo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import com.ufo.ufo.global.security.oauth.OAuthRedirectProperties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("환경별 설정 시작 검증")
class ProfileConfigurationTest {

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @ParameterizedTest
    @CsvSource({
            "dev,update,always,true,com.mysql.cj.jdbc.Driver",
            "test,create-drop,never,false,org.h2.Driver",
            "prod,none,never,false,com.mysql.cj.jdbc.Driver"
    })
    @DisplayName("실제 프로필을 불러오면 DB 정책과 환경 변수 값이 Bean에 적용된다")
    void loadsProfileAndBindsEnvironment(String profile, String ddl, String initMode, boolean showSql,
                                        String driver) {
        MockEnvironment environment = completeEnvironment(null);
        environment.setActiveProfiles(profile);

        try (var context = application(ProfileApplication.class, environment).run()) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo(ddl);
            assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo(initMode);
            assertThat(context.getEnvironment().getProperty("spring.jpa.show-sql", Boolean.class))
                    .isEqualTo(showSql);

            DataSourceProperties database = context.getBean(DataSourceProperties.class);
            assertThat(database.getDriverClassName()).isEqualTo(driver);
            assertThat(database.getUrl()).isEqualTo(environment.getProperty("DB_URL"));
            assertThat(database.getUsername()).isEqualTo("profile-user");

            ImageProperties images = context.getBean(ImageProperties.class);
            assertThat(images.s3().bucket()).isEqualTo("profile-test-bucket");
            assertThat(images.cdnBaseUrl()).isEqualTo("https://cdn.example.test");

            JwtTokenProvider tokens = context.getBean(JwtTokenProvider.class);
            String accessToken = tokens.createAccessToken("user@example.test", "ROLE_USER");
            assertThat(tokens.validateAccessToken(accessToken)).isTrue();

            var refreshCookie = context.getBean(OAuthCookieManager.class)
                    .createRefreshTokenCookie("profile-test-refresh-token", 60, true);
            assertThat(refreshCookie.getDomain()).isEqualTo("example.test");
        }
    }

    @Test
    @DisplayName("테스트 프로필로 실제 DataSource를 생성하고 H2에 연결할 수 있다")
    void testProfileConnectsToH2() throws Exception {
        MockEnvironment environment = completeEnvironment(null);
        environment.setActiveProfiles("test");
        environment.setProperty("DB_USERNAME", "sa");
        environment.setProperty("DB_PASSWORD", "");

        try (var context = application(DatabaseApplication.class, environment).run();
             var connection = context.getBean(DataSource.class).getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "JWT_SECRET,missing", "JWT_SECRET,blank",
            "NAVER_CLIENT_SECRET,missing", "NAVER_CLIENT_SECRET,blank",
            "OAUTH_COOKIE_DOMAIN,missing", "OAUTH_COOKIE_DOMAIN,blank",
            "S3_IMAGE_BUCKET,missing", "DB_URL,missing"
    })
    @DisplayName("필수 값이 없거나 비어 있으면 Bean 생성 전에 누락된 변수 이름으로 시작을 중단한다")
    void rejectsMissingSettingsDuringStartup(String name, String state) {
        MockEnvironment environment = completeEnvironment(name);
        environment.setActiveProfiles("prod");
        if (state.equals("blank")) {
            environment.setProperty(name, " ");
        }

        assertThatThrownBy(() -> {
            try (var ignored = application(ProfileApplication.class, environment).run()) {
                // 실패해야 하는 시작 요청이 성공하더라도 컨텍스트는 닫는다.
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("필수 환경 변수가 없습니다")
                .hasMessageContaining(name)
                .hasMessageNotContaining(JWT_SECRET);
    }

    @Test
    @DisplayName("운영 프로필이 함께 활성화되면 테스트 프로필의 빈 DB 비밀번호 예외를 적용하지 않는다")
    void productionProfileRejectsEmptyPassword() {
        MockEnvironment environment = completeEnvironment(null);
        environment.setActiveProfiles("test", "prod");
        environment.setProperty("DB_PASSWORD", "");

        assertThatThrownBy(() -> {
            try (var ignored = application(ProfileApplication.class, environment).run()) {
            }
        }).isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_PASSWORD");
    }

    private SpringApplication application(Class<?> source, MockEnvironment environment) {
        SpringApplication application = new SpringApplication(source);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        environment.setProperty("spring.config.location", "classpath:/");
        return application;
    }

    private MockEnvironment completeEnvironment(String excluded) {
        MockEnvironment environment = new MockEnvironment();
        String[][] values = {
                {"DB_URL", "jdbc:h2:mem:profile_" + UUID.randomUUID()},
                {"DB_USERNAME", "profile-user"}, {"DB_PASSWORD", "test-password"},
                {"JWT_SECRET", JWT_SECRET}, {"JWT_ACCESS_TOKEN_EXPIRE", "60000"},
                {"REFERRAL_HMAC_SECRET", "profile-test-referral-secret-32-characters"},
                {"S3_IMAGE_BUCKET", "profile-test-bucket"}, {"S3_PUBLIC_BASE_URL", "https://s3.example.test"},
                {"CDN_BASE_URL", "https://cdn.example.test"},
                {"OAUTH_REDIRECT_URL", "https://example.test/auth/callback"},
                {"OAUTH_SIGNUP_REDIRECT_URL", "https://example.test/auth/signup"},
                {"OAUTH_ADMIN_REDIRECT_URL", "https://example.test/admin"},
                {"OAUTH_COOKIE_DOMAIN", "example.test"}
        };
        for (String[] value : values) {
            if (!value[0].equals(excluded)) {
                environment.setProperty(value[0], value[1]);
            }
        }
        for (String provider : new String[] {"GOOGLE", "KAKAO", "NAVER"}) {
            for (String suffix : new String[] {"CLIENT_ID", "CLIENT_SECRET"}) {
                String name = provider + "_" + suffix;
                if (!name.equals(excluded)) {
                    environment.setProperty(name, "profile-test-value");
                }
            }
        }
        return environment;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            DataSourceProperties.class, ImageProperties.class, OAuthRedirectProperties.class
    })
    @Import({JwtTokenProvider.class, OAuthCookieManager.class})
    static class ProfileApplication {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class
    })
    @Import(ProfileApplication.class)
    static class DatabaseApplication {
    }
}
