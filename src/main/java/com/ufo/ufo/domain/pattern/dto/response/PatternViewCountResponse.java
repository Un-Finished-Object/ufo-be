package com.ufo.ufo.domain.pattern.dto.response;

public record PatternViewCountResponse(int viewCount) {

    public static PatternViewCountResponse from(int viewCount) {
        return new PatternViewCountResponse(viewCount);
    }
}
