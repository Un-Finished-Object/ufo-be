package com.ufo.ufo.domain.interest.domain;

import java.util.Arrays;
import java.util.List;

public enum InterestKeyword {
    KNITTING,
    CROCHET,
    PATTERN,
    YARN,
    NEEDLEWORK,
    DIY,
    COMMUNITY,
    TUTORIAL;

    public static boolean isSupported(String keyword) {
        return Arrays.stream(values())
                .anyMatch(value -> value.name().equalsIgnoreCase(keyword));
    }

    public static List<String> names() {
        return Arrays.stream(values())
                .map(Enum::name)
                .toList();
    }
}
