package com.ufo.ufo.domain.user.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidNicknameException extends ApiException {

    public InvalidNicknameException() {
        super(HttpStatus.BAD_REQUEST, "닉네임은 2자 이상 20자 이하여야 합니다.");
    }
}
