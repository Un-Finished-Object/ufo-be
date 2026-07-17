package com.ufo.ufo.domain.alternative.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AlternativeCommentPermissionDeniedException extends ApiException {

    public AlternativeCommentPermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "대체 실 댓글 수정 및 삭제 권한이 없습니다.");
    }
}
