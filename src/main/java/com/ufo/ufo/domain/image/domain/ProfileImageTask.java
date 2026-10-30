package com.ufo.ufo.domain.image.domain;

import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "profile_image_tasks", indexes = {
        @Index(name = "idx_profile_image_task_due", columnList = "next_attempt_at,task_id")
})
public class ProfileImageTask extends BaseEntity {

    public enum Type { LINK, DELETE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type taskType;

    @Column(nullable = false)
    private String imageKey;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    public ProfileImageTask(Long userId, Type taskType, String imageKey) {
        this.userId = userId;
        this.taskType = taskType;
        this.imageKey = imageKey;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public void defer(LocalDateTime now) {
        attempts++;
        nextAttemptAt = now.plusSeconds(30);
    }
}
