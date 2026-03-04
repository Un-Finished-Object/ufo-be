package com.ufo.ufo.domain.credit.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InsufficientCreditException extends ApiException {
    public InsufficientCreditException() {
        super(HttpStatus.BAD_REQUEST, "보유한 볼이 부족합니다.");
    }
}
