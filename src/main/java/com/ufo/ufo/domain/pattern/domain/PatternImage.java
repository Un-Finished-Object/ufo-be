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
@Table(name = "pattern_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatternImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id")
    private Pattern pattern;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    private Integer imageOrder;

    @Builder
    public PatternImage(Pattern pattern, String imageUrl, Integer imageOrder) {
        this.pattern = pattern;
        this.imageUrl = imageUrl;
        this.imageOrder = imageOrder;
    }
}
