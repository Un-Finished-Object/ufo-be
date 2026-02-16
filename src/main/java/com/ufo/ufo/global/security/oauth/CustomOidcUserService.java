package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.dto.GoogleResponse;
import com.ufo.ufo.global.security.dto.KakaoResponse;
import com.ufo.ufo.global.security.dto.NaverResponse;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuthUserUpsertService oauthUserUpsertService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.from(registrationId);

        OAuth2Response oAuth2Response = switch (provider) {
            case GOOGLE -> new GoogleResponse(oidcUser.getAttributes());
            case KAKAO -> new KakaoResponse(oidcUser.getAttributes());
            case NAVER -> new NaverResponse(oidcUser.getAttributes());
        };

        User user = oauthUserUpsertService.saveOrUpdate(oAuth2Response);

        return new CustomOAuth2User(
                user.getEmail(),
                user.getRoleKey(),
                oidcUser.getAttributes(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
    }
}
