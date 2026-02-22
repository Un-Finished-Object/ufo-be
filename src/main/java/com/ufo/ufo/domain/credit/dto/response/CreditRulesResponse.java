package com.ufo.ufo.domain.credit.dto.response;

import java.util.List;

public record CreditRulesResponse(
        int dailyMaxEarnCredits,
        List<Rule> earnRules,
        List<Rule> spendRules
) {
    public record Rule(String key, int amount, String description, boolean dailyLimitExempt) {
    }
}
