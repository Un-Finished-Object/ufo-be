package com.ufo.ufo.domain.pattern.domain;

import com.ufo.ufo.global.base.BaseEntity;
import com.ufo.ufo.domain.user.domain.User;
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
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Table(name = "patterns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pattern extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yarn_id")
    private Yarn yarn;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "designer", nullable = false)
    private String designer;

    @Column(name = "category_main")
    private String categoryMain;

    @Column(name = "category_sub")
    private String categorySub;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "size")
    private String size;

    @Column(name = "actual_size")
    private String actualSize;

    @Column(name = "needle_size")
    private String needleSize;

    @Column(name = "required_yarn_amount")
    private String requiredYarnAmount;

    @Column(name = "gauge")
    private String gauge;

    @Column(name = "scraps_count", nullable = false)
    private Integer scrapsCount;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Pattern(User user, String title, String designer, String categoryMain, String categorySub,
                   String thumbnailUrl, String size, String actualSize, String needleSize,
                   Yarn yarn, String requiredYarnAmount, String gauge, Integer scrapsCount, Integer viewCount) {
        this.user = user;
        this.yarn = yarn;
        this.title = title;
        this.designer = designer;
        this.categoryMain = categoryMain;
        this.categorySub = categorySub;
        this.thumbnailUrl = thumbnailUrl;
        this.size = size;
        this.actualSize = actualSize;
        this.needleSize = needleSize;
        this.requiredYarnAmount = requiredYarnAmount;
        this.gauge = gauge;
        this.scrapsCount = (scrapsCount == null) ? 0 : scrapsCount;
        this.viewCount = (viewCount == null) ? 0 : viewCount;
    }

    public void increaseViewCount() {
        this.viewCount += 1;
    }

    public void increaseScrapsCount() {
        this.scrapsCount += 1;
    }

    public void decreaseScrapsCount() {
        if (this.scrapsCount > 0) {
            this.scrapsCount -= 1;
        }
    }

    public String getOriginalYarnName() {
        if (yarn != null) {
            return yarn.getName();
        }
        return null;
    }
}
