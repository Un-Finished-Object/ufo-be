package com.ufo.ufo.domain.pattern.dto.response;

import java.util.List;

public record PatternListResponse(
        List<PatternListItemResponse> items,
        int page,
        int nextPage
) {
    public static PatternListResponse from(List<PatternListItemResponse> items, int page, int nextPage) {
        return new PatternListResponse(items, page, nextPage);
    }
}
