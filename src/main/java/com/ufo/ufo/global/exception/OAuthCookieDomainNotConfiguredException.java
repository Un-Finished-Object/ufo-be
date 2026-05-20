package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class OAuthCookieDomainNotConfiguredException extends ApiException {

    public OAuthCookieDomainNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "OAuth 쿠키 도메인이 설정되지 않았습니다.");
    }
}
