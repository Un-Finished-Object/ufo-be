package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralService {

    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private final UserService userService;
    private final UserRepository userRepository;
    private final ReferralCodeGenerator referralCodeGenerator;

    @Transactional
    public ReferralCodeResponse createReferralCode(User user) {
        User loginUser = userService.getUserById(user.getId());
        if (loginUser.getReferralCode() == null || loginUser.getReferralCode().isBlank()) {
            loginUser.assignReferralCode(generateUniqueReferralCode(loginUser.getId()));
        }
        return new ReferralCodeResponse(loginUser.getReferralCode());
    }

    public ReferralCodeValidationResponse verifyReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode)
                .map(owner -> new ReferralCodeValidationResponse(true, owner.getNickname()))
                .orElse(new ReferralCodeValidationResponse(false, null));
    }

    private String generateUniqueReferralCode(Long userId) {
        for (int nonce = 0; nonce < MAX_GENERATION_ATTEMPTS; nonce++) {
            String referralCode = referralCodeGenerator.generate(userId, nonce);
            if (!userRepository.existsByReferralCode(referralCode)) {
                return referralCode;
            }
        }
        throw new ReferralCodeGenerationException();
    }
}
