package com.ufo.ufo.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.ufo.ufo.support.logging.LogCapture;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("요청 식별자 필터 테스트")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 처리 중에만 식별자를 로그 문맥에 넣고 처리 후 제거해야 한다")
    void scopesMdcToRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/v1/patterns");
        request.addHeader("X-Request-ID", "request-123");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
            assertThat(MDC.get("requestId")).isEqualTo("request-123");
            assertThat(response.getHeader("X-Request-ID")).isEqualTo("request-123");
        });
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"runtime,false", "servlet,false", "io,false", "runtime,true", "servlet,true", "io,true"})
    @DisplayName("응답 전송 여부와 관계없이 오류 원문을 숨기고 기존 로그 문맥을 복원해야 한다")
    void restoresContextOnFailure(String failureType, boolean committed) {
        MDC.put("requestId", "outer-123");
        MDC.put("other", "preserved");
        var request = new MockHttpServletRequest("GET", "/v1/patterns");
        request.addHeader("X-Request-ID", "inner-123");
        var cause = new IllegalStateException("cause-secret");
        Exception failure = switch (failureType) {
            case "servlet" -> new ServletException("exception-secret", cause);
            case "io" -> new IOException("exception-secret", cause);
            default -> new IllegalStateException("exception-secret", cause);
        };
        failure.addSuppressed(new IllegalArgumentException("suppressed-secret"));
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request, response, (incoming, outgoing) -> {
            if (committed) {
                response.flushBuffer();
            }
            switch (failure) {
                case ServletException exception -> throw exception;
                case IOException exception -> throw exception;
                case RuntimeException exception -> throw exception;
                default -> throw new IllegalStateException(failure);
            }
        })).isInstanceOf(failureType.equals("io") ? IOException.class : ServletException.class)
                .hasNoCause().satisfies(exception -> {
                    assertThat(exception.getMessage())
                            .doesNotContain("exception-secret", "cause-secret", "suppressed-secret");
                    assertThat(exception.getSuppressed()).isEmpty();
                });
        assertThat(MDC.get("requestId")).isEqualTo("outer-123");
        assertThat(MDC.get("other")).isEqualTo("preserved");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("inner-123");
        assertThat(response.isCommitted()).isEqualTo(committed);
    }

    @ParameterizedTest
    @CsvSource({"400,false", "413,false", "400,true", "413,true", "500,false", "500,true"})
    @DisplayName("요청 매개변수 오류는 상태 코드를 유지하고 서버 오류일 때만 ERROR 로그를 남겨야 한다")
    void preservesInvalidParameterStatus(int status, boolean wrapped) {
        var failure = new InvalidParameterException(new RuntimeException("parameter-secret cause-secret"), status);
        failure.addSuppressed(new IllegalArgumentException("suppressed-secret"));
        var request = new MockHttpServletRequest("POST", "/v1/patterns");
        var response = new MockHttpServletResponse();
        try (var logs = new LogCapture(RequestIdFilter.class)) {
            assertThatThrownBy(() -> filter.doFilter(request, response, (incoming, outgoing) -> {
                if (wrapped) {
                    throw new ServletException("wrapper-secret", failure);
                }
                throw failure;
            })).isInstanceOf(InvalidParameterException.class).hasNoCause().satisfies(exception -> {
                assertThat(((InvalidParameterException) exception).getErrorCode()).isEqualTo(status);
                assertThat(exception.getMessage())
                        .doesNotContain("parameter-secret", "wrapper-secret", "cause-secret", "suppressed-secret");
                assertThat(exception.getSuppressed()).isEmpty();
            });
            var errors = logs.events().stream().filter(event -> event.getLevel() == Level.ERROR).toList();
            if (status < 500) {
                assertThat(errors).isEmpty();
            } else {
                assertThat(errors).hasSize(1).allSatisfy(event -> {
                    assertThat(event.getThrowableProxy()).isNull();
                    assertThat(event.getFormattedMessage())
                            .doesNotContain("parameter-secret", "wrapper-secret", "cause-secret", "suppressed-secret");
                });
            }
        }
    }

    @Test
    @DisplayName("중복 헤더나 줄바꿈이 포함된 식별자는 새 식별자로 교체해야 한다")
    void rejectsDuplicateAndInjectedHeaders() throws Exception {
        for (String[] headers : new String[][] {{"request-1", "request-2"}, {"injected\r\nline"}}) {
            var request = new MockHttpServletRequest("GET", "/v1/patterns");
            for (String value : headers) {
                request.addHeader("X-Request-ID", value);
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (incoming, outgoing) -> { });
            assertThat(response.getHeader("X-Request-ID")).matches("[A-Za-z0-9_-]{1,64}")
                    .isNotIn((Object[]) headers);
        }
    }

    @Test
    @DisplayName("같은 요청을 비동기 또는 오류 처리로 다시 전달해도 식별자는 유지해야 한다")
    void reusesIdAcrossDispatches() throws Exception {
        var request = new MockHttpServletRequest("GET", "/v1/patterns");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> { });
        String requestId = response.getHeader("X-Request-ID");
        for (DispatcherType type : new DispatcherType[] {DispatcherType.ASYNC, DispatcherType.ERROR}) {
            request.setDispatcherType(type);
            filter.doFilter(request, response, (incoming, outgoing) ->
                    assertThat(MDC.get("requestId")).isEqualTo(requestId));
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
            assertThat(MDC.get("requestId")).isNull();
        }
    }
}
