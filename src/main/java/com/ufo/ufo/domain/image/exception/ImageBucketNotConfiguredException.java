package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageBucketNotConfiguredException extends ApiException {
    public ImageBucketNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "S3 bucket 설정이 누락되었습니다.");
    }
}
