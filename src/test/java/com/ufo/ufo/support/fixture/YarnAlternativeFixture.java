package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import java.lang.reflect.Field;

public final class YarnAlternativeFixture {

    private YarnAlternativeFixture() {
    }

    public static YarnAlternative create(Yarn originalYarn, Yarn alternativeYarn) {
        return YarnAlternative.builder()
                .originalYarn(originalYarn)
                .alternativeYarn(alternativeYarn)
                .ranking(1)
                .componentScore(100)
                .lengthScore(90)
                .gaugeScore(80)
                .needleScore(70)
                .build();
    }

    public static YarnAlternative createWithId(Long id, Yarn originalYarn, Yarn alternativeYarn) {
        YarnAlternative alternative = create(originalYarn, alternativeYarn);
        setId(alternative, id);
        return alternative;
    }

    public static void setId(YarnAlternative alternative, Long id) {
        try {
            Field idField = YarnAlternative.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(alternative, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
