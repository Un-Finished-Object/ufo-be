package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class OriginalYarnSetNotFoundException extends ApiException {

    public OriginalYarnSetNotFoundException() {
        super(HttpStatus.NOT_FOUND, "원작 실 세트 정보를 찾을 수 없습니다.");
    }
}
