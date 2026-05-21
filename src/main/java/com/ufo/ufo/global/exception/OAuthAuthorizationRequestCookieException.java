package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class OAuthAuthorizationRequestCookieException extends ApiException {

    public OAuthAuthorizationRequestCookieException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "OAuth 인증 요청 쿠키 처리 중 오류가 발생했습니다.");
    }
}
