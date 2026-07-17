package com.ufo.ufo.domain.pattern.domain;

import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "yarns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Yarn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yarn_id")
    private Long yarnId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "vendor", nullable = false)
    private String vendor;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "weight_g")
    private Integer weightG;

    @Column(name = "length")
    private Integer length;

    @Column(name = "ply")
    private Integer ply;

    @Column(name = "main_component")
    private String mainComponent;

    @Column(name = "sub_component")
    private String subComponent;

    @Column(name = "thickness")
    private String thickness;

    @Column(name = "thickness_category")
    private String thicknessCategory;

    @OneToMany(mappedBy = "yarn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<YarnGauge> gauges = new ArrayList<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Yarn(
            String name,
            String vendor,
            Integer price,
            Integer weightG,
            Integer length,
            String mainComponent,
            String subComponent,
            String thickness,
            String thicknessCategory,
            List<YarnGauge> gauges
    ) {
        this.name = name;
        this.vendor = vendor;
        this.price = (price == null) ? 0 : price;
        this.weightG = weightG;
        this.length = length;
        this.mainComponent = mainComponent;
        this.subComponent = subComponent;
        this.thickness = thickness;
        this.thicknessCategory = thicknessCategory;
        replaceGauges(gauges);
    }

    public void update(
            String name,
            String vendor,
            Integer price,
            Integer weightG,
            Integer length,
            String mainComponent,
            String subComponent,
            String thickness,
            String thicknessCategory,
            List<YarnGauge> gauges
    ) {
        this.name = name;
        this.vendor = vendor;
        this.price = (price == null) ? 0 : price;
        this.weightG = weightG;
        this.length = length;
        this.mainComponent = mainComponent;
        this.subComponent = subComponent;
        this.thickness = thickness;
        this.thicknessCategory = thicknessCategory;
        replaceGauges(gauges);
    }

    public void replaceGauges(List<YarnGauge> gauges) {
        this.gauges.clear();
        if (gauges == null) {
            return;
        }
        gauges.forEach(this::addGauge);
    }

    public void addGauge(YarnGauge gauge) {
        if (gauge == null) {
            return;
        }
        gauge.assignYarn(this);
        this.gauges.add(gauge);
    }
}
