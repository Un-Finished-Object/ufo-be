package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PatternSubCategoryRequiredException extends ApiException {

    public PatternSubCategoryRequiredException() {
        super(HttpStatus.BAD_REQUEST, "category가 apparel일 때 subCategory는 필수입니다.");
    }
}
