package com.ufo.ufo.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ufo.ufo.domain.auth.application.AuthSessionCleanup;
import com.ufo.ufo.domain.auth.application.AuthSessionService;
import com.ufo.ufo.domain.auth.dao.AuthSessionRepository;
import com.ufo.ufo.domain.auth.dao.RevokedAccessTokenRepository;
import com.ufo.ufo.domain.image.application.ProfileImageTaskProcessor;
import com.ufo.ufo.domain.image.application.ProfileImageTaskRunner;
import com.ufo.ufo.domain.image.config.ProfileImageTaskSchedulingConfig;
import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.global.security.websocket.AuthenticatedWebSocketSessions;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;

@DisplayName("인증 정리와 이미지 재시도 스케줄 설정 테스트")
class AuthSessionSchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @ParameterizedTest
    @CsvSource({"true,true,3", "false,true,1", "true,false,2", "false,false,0"})
    @DisplayName("인증 정리와 이미지 재시도는 각각의 활성화 설정을 따라야 한다")
    void jobsFollowTheirOwnFlags(boolean authEnabled, boolean imageEnabled, int taskCount) {
        runner.withPropertyValues("app.auth.sessions.scheduling.enabled=" + authEnabled,
                        "app.image.tasks.enabled=" + imageEnabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var processor = context.getBeanProvider(ScheduledAnnotationBeanPostProcessor.class)
                            .getIfAvailable();
                    Set<ScheduledTask> tasks = processor == null ? Set.of() : processor.getScheduledTasks();
                    assertThat(tasks).hasSize(taskCount);
                });
    }

    @Test
    @DisplayName("별도 설정이 없으면 인증 정리와 이미지 재시도를 모두 등록해야 한다")
    void enablesJobsByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(3);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AuthSessionSchedulingConfig.class, ProfileImageTaskSchedulingConfig.class,
            AuthSessionCleanup.class, AuthenticatedWebSocketSessions.class, ProfileImageTaskRunner.class})
    static class TestConfig {

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }

        @Bean
        AuthSessionRepository authSessionRepository() {
            return mock(AuthSessionRepository.class);
        }

        @Bean
        RevokedAccessTokenRepository revokedAccessTokenRepository() {
            return mock(RevokedAccessTokenRepository.class);
        }

        @Bean
        ProfileImageTaskRepository profileImageTaskRepository() {
            return mock(ProfileImageTaskRepository.class);
        }

        @Bean
        ProfileImageTaskProcessor profileImageTaskProcessor() {
            return mock(ProfileImageTaskProcessor.class);
        }
    }
}
