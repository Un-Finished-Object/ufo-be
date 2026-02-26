package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PatternAlternativePermissionDeniedException extends ApiException {
    public PatternAlternativePermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "대체 실 정보 수정 권한이 없습니다.");
    }
}
