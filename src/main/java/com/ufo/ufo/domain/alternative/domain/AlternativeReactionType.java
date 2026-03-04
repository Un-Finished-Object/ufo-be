package com.ufo.ufo.domain.alternative.domain;

import com.ufo.ufo.domain.alternative.exception.InvalidAlternativeReactionTypeException;

public enum AlternativeReactionType {
    LIKE(1),
    DISLIKE(2),
    CANCEL(3);

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
        for (AlternativeReactionType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new InvalidAlternativeReactionTypeException();
    }
}
