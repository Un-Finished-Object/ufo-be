package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
    }
}
