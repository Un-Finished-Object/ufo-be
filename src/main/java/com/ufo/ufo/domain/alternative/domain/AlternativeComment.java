package com.ufo.ufo.domain.alternative.domain;

import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "yarn_alternative_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlternativeComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "yarn_alternative_id", nullable = false)
    private YarnAlternative yarnAlternative;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    public AlternativeComment(YarnAlternative yarnAlternative, User user, String content) {
        this.yarnAlternative = yarnAlternative;
        this.user = user;
        this.content = content;
    }

    public void updateContent(String content) {
        if (Objects.equals(this.content, content)) {
            return;
        }
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(User user) {
        return user != null && user.getId() != null && this.user.getId().equals(user.getId());
    }
}
