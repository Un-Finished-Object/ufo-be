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
        return (String) getAccount().get("email");
    }

    @Override
    public String getName() {
        return (String) getProfile().get("nickname");
    }

    @Override
    public String getProfileImage() {
        return (String) getProfile().get("profile_image_url");
    }

    private Map<String, Object> getAccount() {
        return (Map<String, Object>) attributes.get("kakao_account");
    }

    private Map<String, Object> getProfile() {
        return (Map<String, Object>) getAccount().get("profile");
    }
}
