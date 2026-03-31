package com.ufo.ufo.domain.pattern.domain;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PatternSort {
    NEWS("news"),
    VIEWS("views"),
    SCRAPS("scraps");

    private final String queryValue;

    public static PatternSort from(String value) {
        if (value == null || value.isBlank()) {
            return NEWS;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.queryValue.equalsIgnoreCase(value))
                .findFirst()
                .orElse(NEWS);
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(sort -> sort.queryValue.equalsIgnoreCase(value));
    }
}
