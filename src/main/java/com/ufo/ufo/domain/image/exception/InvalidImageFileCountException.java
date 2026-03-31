package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidImageFileCountException extends ApiException {
    public InvalidImageFileCountException(int maxFileCount) {
        super(HttpStatus.BAD_REQUEST, "fileCount는 1 이상 " + maxFileCount + " 이하여야 합니다.");
    }
}
