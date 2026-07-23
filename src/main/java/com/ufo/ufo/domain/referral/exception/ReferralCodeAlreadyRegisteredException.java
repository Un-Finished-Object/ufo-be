package com.ufo.ufo.domain.referral.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReferralCodeAlreadyRegisteredException extends ApiException {

    public ReferralCodeAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "친구 초대 코드는 한 번만 등록할 수 있습니다.");
    }
}
