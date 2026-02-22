package com.ufo.ufo.domain.referral.dto.response;

public record ReferralCodeValidationResponse(
        boolean valid,
        String ownerNickname
) {
}
