package com.ufo.ufo.global.security.dto;

import com.ufo.ufo.global.security.types.Provider;
import java.util.Map;

public record KakaoResponse(Map<String, Object> attributes) implements OAuth2Response {

    @Override
    public Provider getProvider() {
        return Provider.KAKAO;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getEmail() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        return (String) account.get("email");
    }

    @Override
    public String getName() {
        Map<String, Object> profile = (Map<String, Object>) ((Map<String, Object>) attributes.get("kakao_account")).get("profile");
        return (String) profile.get("nickname");
    }

    @Override
    public String getProfileImage() {
        Map<String, Object> profile = (Map<String, Object>) ((Map<String, Object>) attributes.get("kakao_account")).get("profile");
        return (String) profile.get("profile_image_url");
    }
}
