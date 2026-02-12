package com.ufo.ufo.global.security.dto;

import com.ufo.ufo.global.security.types.Provider;
import java.util.Map;

public record GoogleResponse(Map<String, Object> attributes) implements OAuth2Response {

    @Override
    public Provider getProvider() {
        return Provider.GOOGLE;
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getProfileImage() {
        return (String) attributes.get("picture");
    }
}
