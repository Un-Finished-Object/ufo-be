package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.user.domain.User;

public final class UserInterestFixture {

    private UserInterestFixture() {
    }

    public static UserInterest createUserInterest(User user, String keyword) {
        return UserInterest.builder()
                .user(user)
                .keyword(keyword)
                .build();
    }
}
