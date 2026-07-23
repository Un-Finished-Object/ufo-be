package com.ufo.ufo.domain.referral.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReferralCodeExpiredException extends ApiException {

    public ReferralCodeExpiredException() {
        super(HttpStatus.BAD_REQUEST, "친구 초대 코드는 가입 후 7일 이내에만 등록할 수 있습니다.");
    }
}
