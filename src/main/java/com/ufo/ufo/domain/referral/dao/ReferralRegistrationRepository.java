package com.ufo.ufo.domain.referral.dao;

import com.ufo.ufo.domain.referral.domain.ReferralRegistration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ReferralRegistrationRepository extends JpaRepository<ReferralRegistration, Long> {

    boolean existsByReferee_Id(Long refereeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReferralRegistration> findByReferee_Id(Long refereeId);
}
