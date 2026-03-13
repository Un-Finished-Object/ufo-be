package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidPatternPurchaseTypeException extends ApiException {
    public InvalidPatternPurchaseTypeException() {
        super(HttpStatus.BAD_REQUEST, "type은 chat, yarn 중 하나여야 합니다.");
    }
}
