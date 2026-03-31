package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidImageUrlException extends ApiException {
    public InvalidImageUrlException() {
        super(HttpStatus.BAD_REQUEST, "삭제 가능한 이미지 URL 형식이 아닙니다.");
    }
}
