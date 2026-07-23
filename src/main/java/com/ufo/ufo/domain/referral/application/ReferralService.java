package com.ufo.ufo.domain.referral.application;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.referral.dao.ReferralRegistrationRepository;
import com.ufo.ufo.domain.referral.domain.ReferralRegistration;
import com.ufo.ufo.domain.referral.dto.request.RegisterReferralCodeRequest;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeRegistrationResponse;
import com.ufo.ufo.domain.referral.exception.ReferralCodeAlreadyRegisteredException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeExpiredException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeNotFoundException;
import com.ufo.ufo.domain.referral.exception.SelfReferralCodeException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int MAX_GENERATION_ATTEMPTS = 100;
    private static final int REFERRAL_REWARD_CREDITS = 150;
    private static final int REFERRAL_REGISTRATION_DAYS = 7;

    private final UserService userService;
    private final UserRepository userRepository;
    private final ReferralCodeGenerator referralCodeGenerator;
    private final ReferralCodePersistenceService referralCodePersistenceService;
    private final ReferralRegistrationRepository referralRegistrationRepository;
    private final CreditService creditService;

    @Transactional
    public ReferralCodeResponse getReferralCode(User user) {
        User loginUser = userService.getUserById(user.getId());
        if (loginUser.getReferralCode() != null && !loginUser.getReferralCode().isBlank()) {
            return ReferralCodeResponse.from(loginUser.getNickname(), loginUser.getReferralCode());
        }

        for (int nonce = 0; nonce < MAX_GENERATION_ATTEMPTS; nonce++) {
            String referralCode = referralCodeGenerator.generate(loginUser.getId(), nonce);
            try {
                String savedReferralCode = referralCodePersistenceService.assignAndFlush(loginUser.getId(), referralCode);
                return ReferralCodeResponse.from(loginUser.getNickname(), savedReferralCode);
            } catch (DataIntegrityViolationException exception) {
                // A concurrent request stored the same candidate; retry with the next nonce.
            }
        }
        throw new ReferralCodeGenerationException();
    }

    @Transactional
    public ReferralCodeRegistrationResponse registerReferralCode(User user, RegisterReferralCodeRequest request) {
        User referee = userService.getUserById(user.getId());
        validateRegistrationPeriod(referee);
        if (referralRegistrationRepository.existsByReferee_Id(referee.getId())) {
            throw new ReferralCodeAlreadyRegisteredException();
        }

        User referrer = userRepository.findByReferralCode(request.referralCode())
                .orElseThrow(ReferralCodeNotFoundException::new);
        if (referrer.getId().equals(referee.getId())) {
            throw new SelfReferralCodeException();
        }

        referralRegistrationRepository.saveAndFlush(ReferralRegistration.builder()
                .referee(referee)
                .referrer(referrer)
                .build());
        creditService.addCredits(referee, REFERRAL_REWARD_CREDITS, CreditTransactionType.REFERRAL_BONUS);
        creditService.addCredits(referrer, REFERRAL_REWARD_CREDITS, CreditTransactionType.REFERRAL_BONUS);
        return new ReferralCodeRegistrationResponse(true);
    }

    private void validateRegistrationPeriod(User user) {
        LocalDateTime registrationDeadline = user.getCreatedAt().plusDays(REFERRAL_REGISTRATION_DAYS);
        if (LocalDateTime.now().isAfter(registrationDeadline)) {
            throw new ReferralCodeExpiredException();
        }
    }

}
