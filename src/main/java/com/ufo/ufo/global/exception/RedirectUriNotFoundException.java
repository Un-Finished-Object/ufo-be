package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class RedirectUriNotFoundException extends ApiException {

    public RedirectUriNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "redirect_uri가 존재하지 않습니다.");
    }
}
