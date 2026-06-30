package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnGauge;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

public final class YarnFixture {

    private YarnFixture() {
    }

    public static Yarn createYarn() {
        return Yarn.builder()
                .name("old")
                .weightG(100)
                .mainComponent("wool")
                .subComponent("wool 80%, nylon 20%")
                .vendor("oldV")
                .price(1000)
                .length(120)
                .thickness("oldT")
                .thicknessCategory("Worsted")
                .gauges(List.of(YarnGauge.builder()
                        .needleSize("5.5")
                        .stitch(17)
                        .rowCount(24)
                        .build()))
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

    public static void setDeletedAt(Yarn yarn, LocalDateTime deletedAt) {
        try {
            Field deletedAtField = Yarn.class.getDeclaredField("deletedAt");
            deletedAtField.setAccessible(true);
            deletedAtField.set(yarn, deletedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
