package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidImageSizeException extends ApiException {
    public InvalidImageSizeException(long requestedBytes, long maxBytes) {
        super(
                HttpStatus.BAD_REQUEST,
                "이미지 크기가 정책을 초과했습니다. 요청값: " + requestedBytes + " bytes, 최대값: " + maxBytes + " bytes"
        );
    }
}
