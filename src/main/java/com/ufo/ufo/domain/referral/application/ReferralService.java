package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private final UserService userService;
    private final UserRepository userRepository;
    private final ReferralCodeGenerator referralCodeGenerator;
    private final ReferralCodePersistenceService referralCodePersistenceService;

    public ReferralCodeResponse createReferralCode(User user) {
        User loginUser = userService.getUserById(user.getId());
        if (loginUser.getReferralCode() != null && !loginUser.getReferralCode().isBlank()) {
            return new ReferralCodeResponse(loginUser.getReferralCode());
        }

        for (int nonce = 0; nonce < MAX_GENERATION_ATTEMPTS; nonce++) {
            String referralCode = referralCodeGenerator.generate(loginUser.getId(), nonce);
            try {
                String savedReferralCode = referralCodePersistenceService.assignAndFlush(
                        loginUser.getId(), referralCode);
                return new ReferralCodeResponse(savedReferralCode);
            } catch (DataIntegrityViolationException exception) {
                // A concurrent request stored the same candidate; retry with the next nonce.
            }
        }
        throw new ReferralCodeGenerationException();
    }

    @Transactional(readOnly = true)
    public ReferralCodeValidationResponse verifyReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode)
                .map(owner -> new ReferralCodeValidationResponse(true, owner.getNickname()))
                .orElse(new ReferralCodeValidationResponse(false, null));
    }

}
