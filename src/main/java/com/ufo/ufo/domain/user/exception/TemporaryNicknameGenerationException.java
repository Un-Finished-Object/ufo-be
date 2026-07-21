package com.ufo.ufo.domain.user.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TemporaryNicknameGenerationException extends ApiException {

    public TemporaryNicknameGenerationException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "임시 닉네임을 생성할 수 없습니다.");
    }
}
