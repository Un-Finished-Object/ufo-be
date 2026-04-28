package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.lang.reflect.Field;

public final class YarnFixture {

    private YarnFixture() {
    }

    public static Yarn createYarn() {
        return Yarn.builder()
                .name("old")
                .mainComponent("wool")
                .subComponent("wool 80%, nylon 20%")
                .vendor("oldV")
                .price(1000)
                .length(120)
                .thickness("oldT")
                .thicknessCategory("Worsted")
                .build();
    }

    public static Yarn createYarnWithId(Long id) {
        Yarn yarn = createYarn();
        setId(yarn, id);
        return yarn;
    }

    public static void setId(Yarn yarn, Long id) {
        try {
            Field idField = Yarn.class.getDeclaredField("yarnId");
            idField.setAccessible(true);
            idField.set(yarn, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
