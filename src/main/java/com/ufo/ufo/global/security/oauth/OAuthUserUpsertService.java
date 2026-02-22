package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthUserUpsertService {

    private final UserRepository userRepository;

    @Transactional
    public User saveOrUpdate(OAuth2Response response) {
        User user = userRepository.findByEmail(response.getEmail())
                .orElse(null);

        if (user == null) {
            user = User.builder()
                    .email(response.getEmail())
                    .nickname(response.getName())
                    .profileImage(response.getProfileImage())
                    .role(Role.ROLE_GUEST)
                    .provider(response.getProvider())
                    .ballBalance(0)
                    .build();
        } else {
            user.updateNameAndProfileImage(response.getName(), response.getProfileImage());
        }

        return userRepository.save(user);
    }
}
