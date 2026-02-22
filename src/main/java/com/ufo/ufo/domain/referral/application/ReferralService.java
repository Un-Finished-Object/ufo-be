package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralService {

    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    public ReferralCodeResponse createReferralCode(User user) {
        User loginUser = userService.getUserById(user.getId());
        if (loginUser.getReferralCode() == null || loginUser.getReferralCode().isBlank()) {
            loginUser.assignReferralCode(generateReferralCode());
        }
        return new ReferralCodeResponse(loginUser.getReferralCode());
    }

    public ReferralCodeValidationResponse verifyReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode)
                .map(owner -> new ReferralCodeValidationResponse(true, owner.getNickname()))
                .orElse(new ReferralCodeValidationResponse(false, null));
    }

    private String generateReferralCode() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
    }
}
