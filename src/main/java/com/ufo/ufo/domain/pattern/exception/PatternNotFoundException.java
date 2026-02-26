package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PatternNotFoundException extends ApiException {
    public PatternNotFoundException() {
        super(HttpStatus.NOT_FOUND, "도안을 찾을 수 없습니다.");
    }
}
