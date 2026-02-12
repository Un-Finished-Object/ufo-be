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
        Map<String, Object> response = getResponse();
        return (String) response.get("id");
    }

    @Override
    public String getEmail() {
        Map<String, Object> response = getResponse();
        return (String) response.get("email");
    }

    @Override
    public String getName() {
        Map<String, Object> response = getResponse();
        return (String) response.get("name");
    }

    @Override
    public String getProfileImage() {
        Map<String, Object> response = getResponse();
        return (String) response.get("profile_image");
    }

    private Map<String, Object> getResponse() {
        return (Map<String, Object>) attributes.get("response");
    }
}
