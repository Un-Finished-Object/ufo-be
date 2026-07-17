package com.ufo.ufo.domain.alternative.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AlternativeCommentNotFoundException extends ApiException {

    public AlternativeCommentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "대체 실 댓글을 찾을 수 없습니다.");
    }
}
