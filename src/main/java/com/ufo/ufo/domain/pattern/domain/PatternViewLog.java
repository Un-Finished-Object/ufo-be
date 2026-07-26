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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "pattern_view_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pattern_user_date",
                        columnNames = {"pattern_id", "user_id", "viewed_date"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatternViewLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_view_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", nullable = false)
    private Pattern pattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "viewed_date", nullable = false)
    private LocalDate viewedDate;

    @Builder
    public PatternViewLog(Pattern pattern, User user, LocalDate viewedDate) {
        this.pattern = pattern;
        this.user = user;
        this.viewedDate = viewedDate;
    }
}
