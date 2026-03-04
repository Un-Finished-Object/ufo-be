package com.ufo.ufo.domain.alternative.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AlternativeInteractionPermissionDeniedException extends ApiException {
    public AlternativeInteractionPermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "대체 실 커뮤니티 기능 사용 권한이 없습니다.");
    }
}
