package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PatternFixture {

    private PatternFixture() {
    }

    public static Pattern createPattern() {
        return Pattern.builder()
                .title("patternA")
                .designer("artist")
                .categoryMain("apparel")
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

    public static Pattern createPatternWithInterestNumbers(
            String title,
            String designer,
            String categoryMain,
            String categorySub,
            String thumbnailUrl,
            List<Integer> interestNumbers
    ) {
        return Pattern.builder()
                .title(title)
                .designer(designer)
                .categoryMain(categoryMain)
                .categorySub(categorySub)
                .thumbnailUrl(thumbnailUrl)
                .interestNumbers(interestNumbers)
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

    public static void setYarn(Pattern pattern, Yarn yarn) {
        setOriginalYarn(pattern, yarn, null, null);
    }

    public static void setOriginalYarn(Pattern pattern, Yarn mainYarn, Yarn secondYarn, Yarn subYarn) {
        PatternOriginalYarn originalYarn = PatternOriginalYarn.builder()
                .mainYarn(mainYarn)
                .secondYarn(secondYarn)
                .subYarn(subYarn)
                .build();
        setOriginalPattern(originalYarn, pattern);
        List<PatternOriginalYarn> originalYarns = new ArrayList<>(pattern.getOriginalYarns());
        originalYarns.add(originalYarn);
        setOriginalYarns(pattern, originalYarns);
    }

    public static void replaceOriginalYarns(Pattern pattern, List<PatternOriginalYarn> originalYarns) {
        if (originalYarns == null) {
            setOriginalYarns(pattern, List.of());
            return;
        }
        originalYarns.forEach(originalYarn -> setOriginalPattern(originalYarn, pattern));
        setOriginalYarns(pattern, originalYarns);
    }

    private static void setOriginalPattern(PatternOriginalYarn originalYarn, Pattern pattern) {
        try {
            Field patternField = PatternOriginalYarn.class.getDeclaredField("pattern");
            patternField.setAccessible(true);
            patternField.set(originalYarn, pattern);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setOriginalYarns(Pattern pattern, List<PatternOriginalYarn> originalYarns) {
        try {
            Field originalYarnsField = Pattern.class.getDeclaredField("originalYarns");
            originalYarnsField.setAccessible(true);
            originalYarnsField.set(pattern, originalYarns);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
