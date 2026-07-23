package com.ufo.ufo.domain.alternative.domain;

import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "yarn_alternative_reactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_yarn_alt_reaction_user", columnNames = {"yarn_alternative_id", "user_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlternativeReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "yarn_alternative_id", nullable = false)
    private YarnAlternative yarnAlternative;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AlternativeReactionType type;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public AlternativeReaction(YarnAlternative yarnAlternative, User user, AlternativeReactionType type) {
        this.yarnAlternative = yarnAlternative;
        this.user = user;
        this.type = type;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateType(AlternativeReactionType type) {
        if (this.type == type) {
            return;
        }
        this.type = type;
        this.updatedAt = LocalDateTime.now();
    }
}
