package com.ufo.ufo.domain.referral.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterReferralCodeRequest(
        @NotBlank(message = "referralCode는 필수입니다.")
        String referralCode
) {
}
