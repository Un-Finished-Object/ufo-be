package com.ufo.ufo.global.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerMapping;

public final class ErrorLogSupport {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String LOGGED_ATTRIBUTE = ErrorLogSupport.class.getName() + ".logged";

    private ErrorLogSupport() {
    }

    public static String identifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches() ? value : "[생략]";
    }

    public static void httpFailure(Logger logger, HttpServletRequest request, Throwable failure) {
        if (Boolean.TRUE.equals(request.getAttribute(LOGGED_ATTRIBUTE))) {
            return;
        }
        request.setAttribute(LOGGED_ATTRIBUTE, true);
        Object mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = mapping == null ? "[매핑되지 않은 요청]" : mapping.toString();
        logger.error("HTTP 요청 처리 실패: requestId={}, method={}, path={}\n{}",
                identifier(MDC.get("requestId")), identifier(request.getMethod()), path, stackTrace(failure));
    }

    public static String stackTrace(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendTrace(result, failure, visited, "");
        return result.toString();
    }

    private static void appendTrace(StringBuilder result, Throwable failure, Set<Throwable> visited, String prefix) {
        if (failure == null || !visited.add(failure) || visited.size() > 16) {
            return;
        }
        // 예외 메시지에는 요청 값이 섞일 수 있어 타입과 스택 위치만 기록한다.
        result.append(prefix).append(failure.getClass().getName()).append(" [메시지 생략]\n");
        StackTraceElement[] frames = failure.getStackTrace();
        for (int index = 0; index < Math.min(frames.length, 64); index++) {
            result.append("\tat ").append(frames[index]).append('\n');
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            appendTrace(result, suppressed, visited, "Suppressed: ");
        }
        appendTrace(result, failure.getCause(), visited, "Caused by: ");
    }
}
