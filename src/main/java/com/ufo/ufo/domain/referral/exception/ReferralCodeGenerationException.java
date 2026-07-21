package com.ufo.ufo.domain.referral.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ReferralCodeGenerationException extends ApiException {

    public ReferralCodeGenerationException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "친구 초대 코드를 생성할 수 없습니다.");
    }
}
