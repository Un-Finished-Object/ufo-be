package com.ufo.ufo.domain.credit.domain;

public enum CreditTransactionType {
    ATTENDANCE_DAILY,
    STYLE_POST,
    COMMENT_WRITE,
    ALT_YARN_RECOMMENDED,
    CHATROOM_COLLECTION,
    REFERRAL_BONUS,
    CHATROOM_ENTRY,
    ALT_YARN_VIEW,
    BUNDLE_PURCHASE;

    public boolean isDailyLimitExempt() {
        return this == REFERRAL_BONUS;
    }
}
