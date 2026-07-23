package com.ufo.ufo.domain.credit.policy;

import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import java.util.List;

public final class CreditPolicy {

    public static final int DAILY_MAX_EARN_BALLS = 20;
    public static final int ATTENDANCE_DAILY_BALLS = 20;
    public static final int CHATROOM_ENTRY_COST_BALLS = 10;
    public static final int ALT_YARN_VIEW_COST_BALLS = 10;
    public static final int BUNDLE_PURCHASE_COST_BALLS = 8;

    private CreditPolicy() {
    }

    public static List<CreditRulesResponse.Rule> earnRules() {
        return List.of(
                new CreditRulesResponse.Rule("ATTENDANCE_DAILY", ATTENDANCE_DAILY_BALLS, "매일 00시 이후 최초 접속 시 1회", false),
                new CreditRulesResponse.Rule("STYLE_POST", 5, "스타일 탭에 글 업로드 완료 시", false),
                new CreditRulesResponse.Rule("COMMENT_WRITE", 1, "스타일/커뮤니티 글에 15자 이상 작성 시", false),
                new CreditRulesResponse.Rule("CHATROOM_COLLECTION", 2, "유료 채팅방 입장 3회 누적마다", false),
                new CreditRulesResponse.Rule("REFERRAL_BONUS", 150, "친구 초대 코드 등록 시 쌍방 지급", true)
        );
    }

    public static List<CreditRulesResponse.Rule> spendRules() {
        return List.of(
                new CreditRulesResponse.Rule("CHATROOM_ENTRY", -CHATROOM_ENTRY_COST_BALLS, "특정 도안 채팅방 영구 해금", false),
                new CreditRulesResponse.Rule("ALT_YARN_VIEW", -ALT_YARN_VIEW_COST_BALLS, "특정 도안 대체 실 정보 블러 제거", false),
                new CreditRulesResponse.Rule("BUNDLE_PURCHASE", -BUNDLE_PURCHASE_COST_BALLS, "채팅방+대체 실 동시 해금 (2볼 할인)", false)
        );
    }
}
