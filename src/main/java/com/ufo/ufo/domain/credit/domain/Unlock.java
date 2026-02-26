package com.ufo.ufo.domain.credit.domain;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "unlocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_unlocks_user_pattern_type", columnNames = {"user_id", "pattern_id", "type"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unlock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unlock_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "pattern_id", nullable = false)
    private Long patternId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private UnlockType type;

    @Builder
    public Unlock(User user, Long patternId, UnlockType type) {
        this.user = user;
        this.patternId = patternId;
        this.type = type;
    }
}
