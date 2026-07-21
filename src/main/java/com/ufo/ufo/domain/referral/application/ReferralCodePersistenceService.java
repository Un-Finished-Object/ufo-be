package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferralCodePersistenceService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String assignAndFlush(Long userId, String referralCode) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return user.getReferralCode();
        }
        user.assignReferralCode(referralCode);
        userRepository.saveAndFlush(user);
        return referralCode;
    }
}
