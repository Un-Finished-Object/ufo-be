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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String nickname, String profileImage, Role role, Provider provider) {
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role;
        this.provider = provider;
    }

    public String getRoleKey() {
        return this.role.name();
    }

    public void updateNameAndProfileImage(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }
}
