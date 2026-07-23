package com.ufo.ufo.domain.referral.dto.response;

public record ReferralCodeResponse(
        String username,
        String referralCode
) {
    public static ReferralCodeResponse from(String nickname, String referralCode) {
        return new ReferralCodeResponse(
                nickname,
                referralCode
        );
    }
}
