package com.ufo.ufo.global.security.dto;

import com.ufo.ufo.global.security.types.Provider;
import java.util.Map;

public record NaverResponse(Map<String, Object> attributes) implements OAuth2Response {

    @Override
    public Provider getProvider() {
        return Provider.NAVER;
    }

    @Override
    public String getProviderId() {
        return (String) getResponse().get("id");
    }

    @Override
    public String getEmail() {
        return (String) getResponse().get("email");
    }

    @Override
    public String getName() {
        return (String) getResponse().get("name");
    }

    @Override
    public String getProfileImage() {
        return (String) getResponse().get("profile_image");
    }

    private Map<String, Object> getResponse() {
        return (Map<String, Object>) attributes.get("response");
    }
}
