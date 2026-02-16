package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.dto.GoogleResponse;
import com.ufo.ufo.global.security.dto.KakaoResponse;
import com.ufo.ufo.global.security.dto.NaverResponse;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserUpsertService oauthUserUpsertService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = fetchOAuth2User(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.from(registrationId);

        OAuth2Response oAuth2Response = switch (provider) {
            case KAKAO -> new KakaoResponse(oAuth2User.getAttributes());
            case GOOGLE -> new GoogleResponse(oAuth2User.getAttributes());
            case NAVER -> new NaverResponse(oAuth2User.getAttributes());
        };

        User user = oauthUserUpsertService.saveOrUpdate(oAuth2Response);

        return new CustomOAuth2User(user.getEmail(), user.getRoleKey(), oAuth2User.getAttributes());
    }

    protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
