package com.ufo.ufo.domain.alternative.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AlternativeNotFoundException extends ApiException {
    public AlternativeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "대체 실 정보를 찾을 수 없습니다.");
    }
}
