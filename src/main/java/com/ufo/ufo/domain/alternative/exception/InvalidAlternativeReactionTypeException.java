package com.ufo.ufo.domain.alternative.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidAlternativeReactionTypeException extends ApiException {
    public InvalidAlternativeReactionTypeException() {
        super(HttpStatus.BAD_REQUEST, "type은 1(추천), 2(취소)만 허용됩니다.");
    }
}
