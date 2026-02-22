package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedProviderException extends ApiException {

    public UnsupportedProviderException(String registrationId) {
        super(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다: " + registrationId);
    }
}
