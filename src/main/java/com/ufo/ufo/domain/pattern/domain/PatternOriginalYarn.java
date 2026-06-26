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

@Entity
@Getter
@Table(name = "pattern_original_yarns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatternOriginalYarn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_original_yarn_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id")
    private Pattern pattern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_yarn_id")
    private Yarn mainYarn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_yarn_id")
    private Yarn secondYarn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_yarn_id")
    private Yarn subYarn;

    @Builder
    public PatternOriginalYarn(Pattern pattern, Yarn mainYarn, Yarn secondYarn, Yarn subYarn) {
        this.pattern = pattern;
        this.mainYarn = mainYarn;
        this.secondYarn = secondYarn;
        this.subYarn = subYarn;
    }

    void assignPattern(Pattern pattern) {
        this.pattern = pattern;
    }
}
