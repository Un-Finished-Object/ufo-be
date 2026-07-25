package com.ufo.ufo.domain.credit.domain;

public enum CreditTransactionType {
    SIGNUP_BONUS,
    ATTENDANCE_DAILY,
    REFERRAL_BONUS,
    CHATROOM_ENTRY,
    ALT_YARN_VIEW;

    public boolean isDailyLimitExempt() {
        return this == SIGNUP_BONUS || this == REFERRAL_BONUS;
    }
}
