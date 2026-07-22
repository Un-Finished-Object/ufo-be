package com.ufo.ufo.domain.pattern.domain;

import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "yarn_alternatives",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_yarn_alternative_original_alternative",
                        columnNames = {"original_yarn_id", "alternative_yarn_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnAlternative extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_yarn_id", nullable = false)
    private Yarn originalYarn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alternative_yarn_id", nullable = false)
    private Yarn alternativeYarn;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    @Column(name = "component_score", nullable = false)
    private Integer componentScore;

    @Column(name = "length_score", nullable = false)
    private Integer lengthScore;

    @Column(name = "gauge_score", nullable = false)
    private Integer gaugeScore;

    @Column(name = "needle_score", nullable = false)
    private Integer needleScore;

    @Builder
    public YarnAlternative(
            Yarn originalYarn,
            Yarn alternativeYarn,
            Integer ranking,
            Integer componentScore,
            Integer lengthScore,
            Integer gaugeScore,
            Integer needleScore
    ) {
        this.originalYarn = originalYarn;
        this.alternativeYarn = alternativeYarn;
        this.ranking = ranking;
        this.componentScore = componentScore;
        this.lengthScore = lengthScore;
        this.gaugeScore = gaugeScore;
        this.needleScore = needleScore;
    }
}
