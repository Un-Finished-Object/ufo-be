package com.ufo.ufo.domain.pattern.validation;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PatternCategoryQuery {
    ALL("all"),
    APPAREL("apparel"),
    BAGS("bags"),
    ACCESSORIES("accessories"),
    OTHERS("others");

    private final String queryValue;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(category -> category.queryValue.equalsIgnoreCase(value));
    }
}
