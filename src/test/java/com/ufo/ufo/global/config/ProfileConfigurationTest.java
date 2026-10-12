package com.ufo.ufo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

@DisplayName("환경별 설정 검증")
class ProfileConfigurationTest {

    @Test
    @DisplayName("공통 설정에는 환경별 DB 및 SQL 정책을 두지 않는다")
    void commonConfigurationHasNoDatabasePolicy() {
        Properties properties = load("application.yaml");

        assertThat(properties).doesNotContainKeys(
                "spring.sql.init.mode", "spring.jpa.hibernate.ddl-auto", "spring.jpa.show-sql");
    }

    @Test
    @DisplayName("개발·운영·테스트 설정은 DB 계정과 JWT·OAuth 값을 환경 변수에서 읽는다")
    void profileCredentialsComeFromEnvironment() {
        for (String profile : new String[] {"dev", "prod", "test"}) {
            Properties properties = load("application-" + profile + ".yml");
            assertThat(properties.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
            assertThat(properties.getProperty("spring.datasource.username"))
                    .isEqualTo("${DB_USERNAME}");
            assertThat(properties.getProperty("spring.datasource.password"))
                    .isEqualTo("${DB_PASSWORD}");
            assertThat(properties.getProperty("spring.jwt.secret")).isEqualTo("${JWT_SECRET}");
            assertThat(properties.getProperty("spring.jwt.access-token-expire"))
                    .isEqualTo("${JWT_ACCESS_TOKEN_EXPIRE:7200000}");
            assertThat(properties.getProperty("spring.jwt.refresh-token-expire"))
                    .isEqualTo("${JWT_REFRESH_TOKEN_EXPIRE:1209600000}");

            for (String provider : new String[] {"google", "kakao", "naver"}) {
                String prefix = "spring.security.oauth2.client.registration." + provider + ".";
                String envPrefix = provider.toUpperCase() + "_";
                assertThat(properties.getProperty(prefix + "client-id"))
                        .isEqualTo("${" + envPrefix + "CLIENT_ID}");
                assertThat(properties.getProperty(prefix + "client-secret"))
                        .isEqualTo("${" + envPrefix + "CLIENT_SECRET}");
            }
        }
    }

    @Test
    @DisplayName("운영 설정은 스키마 자동 변경과 SQL 초기화·출력을 비활성화한다")
    void productionDatabasePolicyIsSafe() {
        Properties properties = load("application-prod.yml");

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }

    private Properties load(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        return factory.getObject();
    }
}
