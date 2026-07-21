package com.ufo.ufo.domain.user.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateNicknameException extends ApiException {

    public DuplicateNicknameException() {
        super(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
    }
}
