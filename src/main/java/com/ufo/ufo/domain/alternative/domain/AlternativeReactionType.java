package com.ufo.ufo.domain.alternative.domain;

import com.ufo.ufo.domain.alternative.exception.InvalidAlternativeReactionTypeException;

public enum AlternativeReactionType {
    LIKE(1),
    CANCEL(2),
    @Deprecated
    DISLIKE(0);

    private final int code;

    AlternativeReactionType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isCancel() {
        return this == CANCEL;
    }

    public static AlternativeReactionType from(int code) {
        return switch (code) {
            case 1 -> LIKE;
            case 2 -> CANCEL;
            default -> throw new InvalidAlternativeReactionTypeException();
        };
    }
}
