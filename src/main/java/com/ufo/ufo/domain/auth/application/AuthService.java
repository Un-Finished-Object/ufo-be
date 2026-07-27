package com.ufo.ufo.domain.auth.application;

import com.ufo.ufo.domain.auth.dto.request.SignupRequest;
import com.ufo.ufo.domain.auth.dto.response.SignupResponse;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.NicknamePolicy;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InterestService interestService;
    private final ImageService imageService;
    private final CreditService creditService;
    private final ReferralService referralService;

    @Transactional
    public SignupResponse signup(User user, SignupRequest request) {
        String normalizedNickname = NicknamePolicy.normalizeAndValidate(request.userName());
        User loginUser = userService.getUserByIdForUpdate(user.getId());
        User updatedUser = userService.updateNameAndProfileImage(
                loginUser, normalizedNickname, request.profileImageKey());
        List<String> keywords = interestService.replaceMyInterests(loginUser, request.keywords());
        boolean wasGuest = loginUser.isGuest();
        loginUser.promoteToUserIfGuest();
        if (wasGuest) {
            creditService.addCredits(loginUser, CreditPolicy.SIGNUP_BONUS_BALLS, CreditTransactionType.SIGNUP_BONUS);
        }
        referralService.ensureReferralCode(loginUser);

        return new SignupResponse(
                updatedUser.getId(),
                updatedUser.getNickname(),
                imageService.buildImageUrl(updatedUser.getProfileImage()),
                keywords
        );
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException();
        }

        String email = jwtTokenProvider.getAuthentication(refreshToken)
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        String newAccessToken = jwtTokenProvider.createAccessToken(email, user.getRoleKey());

        return new TokenResponse(newAccessToken, JwtTokenProvider.BEARER_TYPE, jwtTokenProvider.getAccessTokenExpireTime());
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
