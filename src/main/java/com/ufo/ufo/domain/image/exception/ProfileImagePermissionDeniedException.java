package com.ufo.ufo.domain.image.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ProfileImagePermissionDeniedException extends ApiException {
    public ProfileImagePermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");
    }
}
