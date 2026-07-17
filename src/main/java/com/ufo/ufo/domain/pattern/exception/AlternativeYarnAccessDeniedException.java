package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AlternativeYarnAccessDeniedException extends ApiException {

    public AlternativeYarnAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "대체 실 정보를 조회할 권한이 없습니다.");
    }
}
