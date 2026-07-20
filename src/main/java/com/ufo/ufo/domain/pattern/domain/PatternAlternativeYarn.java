package com.ufo.ufo.domain.pattern.domain;

import com.ufo.ufo.domain.user.domain.User;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "pattern_alternative_yarns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatternAlternativeYarn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id")
    private Pattern pattern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "yarn_id")
    private Yarn yarn;

    @Column(name = "recommended_rewarded_at")
    private LocalDateTime recommendedRewardedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public PatternAlternativeYarn(Pattern pattern, User user, Yarn yarn) {
        this.pattern = pattern;
        this.user = user;
        this.yarn = yarn;
    }

    public void update(Yarn yarn) {
        this.yarn = yarn;
    }

    public boolean isOwnedBy(User user) {
        return this.user != null && user != null && this.user.getId().equals(user.getId());
    }

    public boolean canRewardForRecommended(long likesCount, int threshold) {
        return this.user != null && this.recommendedRewardedAt == null && likesCount > threshold;
    }

    public void markRecommendedRewarded() {
        this.recommendedRewardedAt = LocalDateTime.now();
    }
}
