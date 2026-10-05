package com.ufo.ufo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.support.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("예상하지 못한 HTTP 오류 로그 테스트")
class GlobalExceptionHandlerTest {

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

        @GetMapping("/failure/{name}")
        void fail() {
            var exception = new IllegalStateException("exception-secret", new RuntimeException("cause-secret"));
            exception.addSuppressed(new IllegalArgumentException("suppressed-secret"));
            throw exception;
        }
    }
}
