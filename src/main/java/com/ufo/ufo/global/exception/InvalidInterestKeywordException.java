package com.ufo.ufo.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidInterestKeywordException extends ApiException {

    public InvalidInterestKeywordException(String keyword) {
        super(HttpStatus.BAD_REQUEST, "유효하지 않은 관심사 키워드입니다: " + keyword);
    }
}
