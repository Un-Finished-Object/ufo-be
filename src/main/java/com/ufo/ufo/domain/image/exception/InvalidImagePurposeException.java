package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidImagePurposeException extends ApiException {
    public InvalidImagePurposeException() {
        super(HttpStatus.BAD_REQUEST, "purpose는 STYLE, PROFILE, PATTERN만 허용됩니다.");
    }
}
