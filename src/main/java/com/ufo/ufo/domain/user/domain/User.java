package com.ufo.ufo.domain.user.domain;

import com.ufo.ufo.global.base.BaseEntity;
import com.ufo.ufo.global.security.types.Provider;
import com.ufo.ufo.global.security.types.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Column(nullable = false)
    private Integer ballBalance;

    @Column(unique = true)
    private String referralCode;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String nickname, String profileImage, Role role, Provider provider,
                Integer ballBalance, String referralCode) {
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role;
        this.provider = provider;
        this.ballBalance = (ballBalance == null) ? 0 : ballBalance;
        this.referralCode = referralCode;
    }

    public String getRoleKey() {
        return this.role.name();
    }

    public void updateNameAndProfileImage(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }

    public void promoteToUserIfGuest() {
        if (this.role == Role.ROLE_GUEST) {
            this.role = Role.ROLE_USER;
        }
    }

    public boolean isGuest() {
        return this.role == Role.ROLE_GUEST;
    }

    public void addCredits(int amount) {
        this.ballBalance += amount;
    }

    public void assignReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }
}
