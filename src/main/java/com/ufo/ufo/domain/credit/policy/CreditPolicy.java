package com.ufo.ufo.domain.credit.policy;

import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import java.util.List;

public final class CreditPolicy {

    public static final int DAILY_MAX_EARN_BALLS = 20;
    public static final int SIGNUP_BONUS_BALLS = 150;
    public static final int ATTENDANCE_DAILY_BALLS = 10;
    public static final int REFERRAL_BONUS_BALLS = 150;
    public static final int CHATROOM_ENTRY_COST_BALLS = 10;
    public static final int ALT_YARN_VIEW_COST_BALLS = 10;

    private CreditPolicy() {
    }

    public static List<CreditRulesResponse.Rule> earnRules() {
        return List.of(
                new CreditRulesResponse.Rule("SIGNUP_BONUS", SIGNUP_BONUS_BALLS, "회원가입 완료 시 1회 지급", true),
                new CreditRulesResponse.Rule("ATTENDANCE_DAILY", ATTENDANCE_DAILY_BALLS, "매일 00시 이후 최초 접속 시 1회", false),
                new CreditRulesResponse.Rule("REFERRAL_BONUS", REFERRAL_BONUS_BALLS, "친구 초대 코드 등록 시 쌍방 지급", true)
        );
    }

    public static List<CreditRulesResponse.Rule> spendRules() {
        return List.of(
                new CreditRulesResponse.Rule("CHATROOM_ENTRY", -CHATROOM_ENTRY_COST_BALLS, "특정 도안 채팅방 영구 해금", false),
                new CreditRulesResponse.Rule("ALT_YARN_VIEW", -ALT_YARN_VIEW_COST_BALLS, "특정 도안 대체 실 정보 블러 제거", false)
        );
    }
}
