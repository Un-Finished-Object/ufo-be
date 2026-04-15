package com.ufo.ufo.domain.pattern.validation;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PatternSubCategoryQuery {
    ALL("all"),
    OUTER("outer"),
    SWEATER("sweater"),
    VEST("vest"),
    DRESS("dress"),
    OTHERS("others");

    private final String queryValue;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(subCategory -> subCategory.queryValue.equalsIgnoreCase(value));
    }

    public static boolean isValidNullable(String value) {
        if (value == null) {
            return true;
        }
        return isValid(value);
    }
}
