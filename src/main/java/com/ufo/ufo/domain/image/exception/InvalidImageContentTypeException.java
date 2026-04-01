package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

public class InvalidImageContentTypeException extends ApiException {
    public InvalidImageContentTypeException(String contentType, List<String> allowedContentTypes) {
        super(
                HttpStatus.BAD_REQUEST,
                "허용되지 않은 contentType입니다. 요청값: " + contentType + ", 허용값: " + String.join(", ", allowedContentTypes)
        );
    }
}
