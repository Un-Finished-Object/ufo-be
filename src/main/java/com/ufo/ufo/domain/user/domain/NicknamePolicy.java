package com.ufo.ufo.domain.user.domain;

import com.ufo.ufo.domain.user.exception.InvalidNicknameException;

public final class NicknamePolicy {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;

    private NicknamePolicy() {
    }

    public static String normalizeAndValidate(String nickname) {
        if (nickname == null) {
            throw new InvalidNicknameException();
        }
        String normalizedNickname = nickname.trim();
        if (normalizedNickname.length() < MIN_LENGTH || normalizedNickname.length() > MAX_LENGTH) {
            throw new InvalidNicknameException();
        }
        return normalizedNickname;
    }
}
