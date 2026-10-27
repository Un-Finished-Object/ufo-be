package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccessTokenException extends ApiException {

    public InvalidAccessTokenException() {
        super(HttpStatus.UNAUTHORIZED, "유효하지 않은 Access Token입니다.");
    }
}
