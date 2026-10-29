package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageStorageUnavailableException extends ApiException {

    public ImageStorageUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소에 접근할 수 없습니다.", cause);
    }
}
