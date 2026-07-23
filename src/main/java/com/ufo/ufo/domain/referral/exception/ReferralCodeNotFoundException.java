package com.ufo.ufo.domain.referral.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReferralCodeNotFoundException extends ApiException {

    public ReferralCodeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "존재하지 않는 친구 초대 코드입니다.");
    }
}
