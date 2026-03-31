package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PatternSubCategoryNotAllowedException extends ApiException {

    public PatternSubCategoryNotAllowedException() {
        super(HttpStatus.BAD_REQUEST, "subCategory는 category가 apparel일 때만 사용할 수 있습니다.");
    }
}
