package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class YarnNotFoundException extends ApiException {
    public YarnNotFoundException() {
        super(HttpStatus.NOT_FOUND, "실을 찾을 수 없습니다.");
    }
}
