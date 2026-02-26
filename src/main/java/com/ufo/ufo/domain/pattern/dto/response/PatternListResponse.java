package com.ufo.ufo.domain.pattern.dto.response;

import java.util.List;

public record PatternListResponse(
        List<PatternListItemResponse> items,
        int page
) {
    public static PatternListResponse from(List<PatternListItemResponse> items, int page) {
        return new PatternListResponse(items, page);
    }
}
