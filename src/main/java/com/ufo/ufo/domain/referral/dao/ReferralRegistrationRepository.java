package com.ufo.ufo.domain.referral.dao;

import com.ufo.ufo.domain.referral.domain.ReferralRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRegistrationRepository extends JpaRepository<ReferralRegistration, Long> {

    boolean existsByReferee_Id(Long refereeId);
}
