package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageUploadPolicyException extends ApiException {

    public ImageUploadPolicyException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드 요청을 생성하지 못했습니다.", cause);
    }
}
