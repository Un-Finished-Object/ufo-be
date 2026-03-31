package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageDeletePermissionDeniedException extends ApiException {
    public ImageDeletePermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "본인이 업로드한 이미지만 삭제할 수 있습니다.");
    }
}
