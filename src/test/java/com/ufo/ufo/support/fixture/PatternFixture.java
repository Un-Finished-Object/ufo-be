package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.lang.reflect.Field;
import java.time.LocalDateTime;

public final class PatternFixture {

    private PatternFixture() {
    }

    public static Pattern createPattern() {
        return Pattern.builder()
                .title("patternA")
                .designer("artist")
                .categoryMain("clothing")
                .categorySub("sweater")
                .thumbnailUrl("./patterns/1.png")
                .build();
    }

    public static Pattern createPattern(String title, String designer, String categoryMain, String categorySub, String thumbnailUrl) {
        return Pattern.builder()
                .title(title)
                .designer(designer)
                .categoryMain(categoryMain)
                .categorySub(categorySub)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    public static Pattern createPatternWithId(Long id) {
        Pattern pattern = createPattern();
        setId(pattern, id);
        return pattern;
    }

    public static void setId(Pattern pattern, Long id) {
        try {
            Field idField = Pattern.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(pattern, id);

            Field createdAtField = Pattern.class.getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(pattern, LocalDateTime.now());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setDeletedAt(Pattern pattern, LocalDateTime deletedAt) {
        try {
            Field deletedAtField = Pattern.class.getDeclaredField("deletedAt");
            deletedAtField.setAccessible(true);
            deletedAtField.set(pattern, deletedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
