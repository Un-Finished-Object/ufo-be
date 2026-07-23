package com.ufo.ufo.domain.referral.domain;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "referral_registrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_referral_registrations_referee", columnNames = "referee_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralRegistration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private User referee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    @Builder
    public ReferralRegistration(User referee, User referrer) {
        this.referee = referee;
        this.referrer = referrer;
    }
}
