package com.ufo.ufo.global.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            ErrorLogSupport.httpFailure(log, request, exception);
            // 컨테이너 로그에 원문이 다시 남지 않도록 원래 예외는 전달하지 않는다.
            Throwable parameterFailure = exception instanceof InvalidParameterException
                    ? exception : exception.getCause();
            if (parameterFailure instanceof InvalidParameterException invalidParameter) {
                throw new InvalidParameterException("HTTP 요청 매개변수 오류", invalidParameter.getErrorCode());
            }
            if (exception instanceof IOException) {
                throw new IOException("HTTP 요청 처리 실패");
            }
            throw new ServletException("HTTP 요청 처리 실패");
        } finally {
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    private String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value) {
            return value;
        }
        List<String> headers = Collections.list(request.getHeaders(HEADER_NAME));
        String supplied = headers.size() == 1 ? headers.getFirst() : null;
        String selected = "[생략]".equals(ErrorLogSupport.identifier(supplied))
                ? UUID.randomUUID().toString() : supplied;
        request.setAttribute(ATTRIBUTE, selected);
        return selected;
    }
}
