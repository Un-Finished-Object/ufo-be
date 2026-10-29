package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProfileImageNotUploadedException extends ApiException {

    public ProfileImageNotUploadedException() {
        super(HttpStatus.BAD_REQUEST, "업로드된 프로필 이미지를 찾을 수 없습니다.");
    }
}
