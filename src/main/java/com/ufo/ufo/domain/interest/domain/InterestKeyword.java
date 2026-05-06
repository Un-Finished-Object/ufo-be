package com.ufo.ufo.domain.interest.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterestKeyword {
    VINTAGE(1, "빈티지"),
    CLASSIC(2, "클래식"),
    ROMANTIC(3, "로맨틱"),
    CASUAL(4, "캐주얼"),
    OVERSIZED(5, "오버사이즈"),
    SLIM_FIT(6, "슬림핏"),
    CROP(7, "크롭"),
    REGULAR_FIT(8, "레귤러핏"),
    ARAN_PATTERN(9, "아란무늬"),
    STOCKINETTE(10, "메리야스"),
    COLOR_BLOCK(11, "배색");

    private final int number;
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

    public static Optional<Integer> findNumberByKeyword(String keyword) {
        return Arrays.stream(values())
                .filter(value -> value.keyword.equals(keyword))
                .map(InterestKeyword::getNumber)
                .findFirst();
    }
}
