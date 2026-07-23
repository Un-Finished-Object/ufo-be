package com.ufo.ufo.domain.referral.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class SelfReferralCodeException extends ApiException {

    public SelfReferralCodeException() {
        super(HttpStatus.BAD_REQUEST, "본인의 친구 초대 코드는 등록할 수 없습니다.");
    }
}
