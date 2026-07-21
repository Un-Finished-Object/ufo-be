package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.user.application.TemporaryNicknameGenerator;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.exception.TemporaryNicknameGenerationException;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthUserUpsertService {

    private static final int MAX_SAVE_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final ImageProperties imageProperties;
    private final TemporaryNicknameGenerator temporaryNicknameGenerator;
    private final OAuthUserPersistenceService oAuthUserPersistenceService;

    public User saveOrUpdate(OAuth2Response response) {
        User existingUser = userRepository.findByEmail(response.getEmail()).orElse(null);
        if (existingUser != null) {
            return userRepository.save(existingUser);
        }

        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            User newUser = createUser(response);
            try {
                return oAuthUserPersistenceService.saveAndFlush(newUser);
            } catch (DataIntegrityViolationException exception) {
                User concurrentlyCreatedUser = userRepository.findByEmail(response.getEmail()).orElse(null);
                if (concurrentlyCreatedUser != null) {
                    return concurrentlyCreatedUser;
                }
            }
        }
        throw new TemporaryNicknameGenerationException();
    }

    private User createUser(OAuth2Response response) {
        return User.builder()
                .email(response.getEmail())
                .nickname(temporaryNicknameGenerator.generate(response.getName()))
                .profileImage(imageProperties.defaultProfileImageKey())
                .role(Role.ROLE_GUEST)
                .provider(response.getProvider())
                .ballBalance(0)
                .build();
    }
}
