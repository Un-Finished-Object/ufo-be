package com.ufo.ufo.domain.pattern.domain;

import java.util.Arrays;

public enum PatternSort {
    NEWS("news"),
    VIEWS("views"),
    SCRAPS("scraps");

    private final String queryValue;

    PatternSort(String queryValue) {
        this.queryValue = queryValue;
    }

    public static PatternSort from(String value) {
        if (value == null || value.isBlank()) {
            return NEWS;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.queryValue.equalsIgnoreCase(value))
                .findFirst()
                .orElse(NEWS);
    }
}
