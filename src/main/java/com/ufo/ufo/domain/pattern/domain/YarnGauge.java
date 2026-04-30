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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Table(name = "yarn_gauges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnGauge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "yarn_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Yarn yarn;

    @Column(name = "needle_size")
    private String needleSize;

    @Column(name = "stitch")
    private Integer stitch;

    @Column(name = "row_count")
    private Integer rowCount;

    @Builder
    public YarnGauge(String needleSize, Integer stitch, Integer rowCount) {
        this.needleSize = needleSize;
        this.stitch = stitch;
        this.rowCount = rowCount;
    }

    void assignYarn(Yarn yarn) {
        this.yarn = yarn;
    }
}
