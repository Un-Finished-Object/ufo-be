package com.ufo.ufo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.support.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("예상하지 못한 HTTP 오류 로그 테스트")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("API 클라이언트 오류는 지정한 상태로 응답하고 서버 오류 로그를 남기지 않는다")
    void returnsApiClientFailureWithoutServerErrorLog() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        try (var capture = new LogCapture(GlobalExceptionHandler.class)) {
            mvc.perform(get("/api-client-failure")).andExpect(status().isUnauthorized());

            assertThat(capture.events()).isEmpty();
        }
    }

    @Test
    @DisplayName("API 서버 오류는 원인 메시지를 숨기고 원인 타입과 스택을 기록한다")
    void recordsApiServerFailureWithoutSecrets() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        try (var capture = new LogCapture(GlobalExceptionHandler.class)) {
            var result = mvc.perform(get("/api-failure"))
                    .andExpect(status().isInternalServerError()).andReturn();

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .contains("/api-failure", "ApiException", "IllegalStateException")
                    .doesNotContain("cause-secret");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("cause-secret");
        }
    }

    @Test
    @DisplayName("서버 오류는 요청 메서드와 매핑 경로 및 예외 스택을 로그에 남겨야 한다")
    void recordsUnexpectedFailureWithoutRequestSecrets() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        try (var capture = new LogCapture(GlobalExceptionHandler.class)) {
            var result = mvc.perform(get("/failure/user@example.com")
                            .queryParam("token", "query-secret")
                            .header("Authorization", "Bearer header-secret")
                            .header("Cookie", "refreshToken=cookie-secret"))
                    .andExpect(status().isInternalServerError()).andReturn();

            assertThat(capture.events()).hasSize(1);
            String message = capture.events().getFirst().getFormattedMessage();
            assertThat(message).contains("GET", "/failure/{name}", "IllegalStateException", "FailingController");
            assertThat(message).doesNotContain("user@example.com", "query-secret", "header-secret", "cookie-secret",
                    "exception-secret", "cause-secret", "suppressed-secret");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("exception-secret", "cause-secret");
            assertThat(capture.events().getFirst().getThrowableProxy()).isNull();
        }
    }

    @RestController
    static class FailingController {

        @GetMapping("/api-client-failure")
        void apiClientFail() {
            throw new InvalidAccessTokenException();
        }

        @GetMapping("/api-failure")
        void apiFail() {
            var exception = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");
            exception.initCause(new IllegalStateException("cause-secret"));
            throw exception;
        }

        @GetMapping("/failure/{name}")
        void fail() {
            var exception = new IllegalStateException("exception-secret", new RuntimeException("cause-secret"));
            exception.addSuppressed(new IllegalArgumentException("suppressed-secret"));
            throw exception;
        }
    }
}
