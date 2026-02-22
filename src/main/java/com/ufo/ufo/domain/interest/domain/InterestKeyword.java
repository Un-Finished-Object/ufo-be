package com.ufo.ufo.domain.interest.domain;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterestKeyword {
    VINTAGE("빈티지"),
    CLASSIC("클래식"),
    ROMANTIC("로맨틱"),
    CASUAL("캐주얼"),
    OVERSIZED("오버사이즈"),
    SLIM_FIT("슬림핏"),
    CROP("크롭"),
    REGULAR_FIT("레귤러핏"),
    ARAN_PATTERN("아란무늬"),
    STOCKINETTE("메리야스"),
    COLOR_BLOCK("배색");

    private final String keyword;

    public static boolean isSupported(String keyword) {
        return Arrays.stream(values())
                .anyMatch(value -> value.keyword.equals(keyword));
    }

    public static List<String> names() {
        return Arrays.stream(values())
                .map(InterestKeyword::getKeyword)
                .toList();
    }
}
