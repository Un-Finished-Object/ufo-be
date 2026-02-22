package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedUserException extends ApiException {

    public UnauthorizedUserException() {
        super(HttpStatus.UNAUTHORIZED, "인증 사용자 이메일을 확인할 수 없습니다.");
    }
}
