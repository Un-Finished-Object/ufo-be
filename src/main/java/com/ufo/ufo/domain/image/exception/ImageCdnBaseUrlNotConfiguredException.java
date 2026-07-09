package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ImageCdnBaseUrlNotConfiguredException extends ApiException {
    public ImageCdnBaseUrlNotConfiguredException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "CDN Base URL 설정이 누락되었습니다.");
    }
}
