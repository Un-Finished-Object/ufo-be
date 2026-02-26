package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.user.domain.User;
import java.lang.reflect.Field;

public final class PatternAlternativeYarnFixture {

    private PatternAlternativeYarnFixture() {
    }

    public static PatternAlternativeYarn create(Pattern pattern, User user, Yarn yarn) {
        return PatternAlternativeYarn.builder()
                .pattern(pattern)
                .user(user)
                .yarn(yarn)
                .gauge("oldGauge")
                .imageUrl("./yarns/20.png")
                .build();
    }

    public static PatternAlternativeYarn createWithId(Long id, Pattern pattern, User user, Yarn yarn) {
        PatternAlternativeYarn alternative = create(pattern, user, yarn);
        setId(alternative, id);
        return alternative;
    }

    public static void setId(PatternAlternativeYarn alternative, Long id) {
        try {
            Field idField = PatternAlternativeYarn.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(alternative, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
