package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidProfileImageUrlException extends ApiException {
    public InvalidProfileImageUrlException() {
        super(HttpStatus.BAD_REQUEST, "프로필 이미지 객체 키 형식이 올바르지 않습니다.");
    }
}
