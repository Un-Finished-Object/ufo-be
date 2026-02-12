package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.dto.GoogleResponse;
import com.ufo.ufo.global.security.dto.KakaoResponse;
import com.ufo.ufo.global.security.dto.NaverResponse;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Provider;
import com.ufo.ufo.global.security.types.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.from(registrationId);

        OAuth2Response oAuth2Response = switch (provider) {
            case KAKAO -> new KakaoResponse(oAuth2User.getAttributes());
            case GOOGLE -> new GoogleResponse(oAuth2User.getAttributes()); // ✅ 이제 사용 가능
            case NAVER -> new NaverResponse(oAuth2User.getAttributes());   // ✅ 이제 사용 가능
        };

        User user = saveOrUpdate(oAuth2Response);

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private User saveOrUpdate(OAuth2Response response) {
        User user = userRepository.findByEmail(response.getEmail())
                .orElse(null);

        if (user == null) {
            user = User.builder()
                    .email(response.getEmail())
                    .nickname(response.getName())
                    .profileImage(response.getProfileImage())
                    .role(Role.ROLE_GUEST)
                    .provider(response.getProvider())
                    .build();
        } else {
            user.updateNameAndProfileImage(response.getName(), response.getProfileImage());
        }

        return userRepository.save(user);
    }
}
