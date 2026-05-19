package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class OAuthRedirectUrlNotConfiguredException extends ApiException {

    public OAuthRedirectUrlNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "OAuth 리다이렉트 URL이 설정되지 않았습니다.");
    }
}
