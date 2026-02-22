package com.ufo.ufo.global.security.dto;

import com.ufo.ufo.global.security.types.Provider;

public interface OAuth2Response {
    Provider getProvider();
    String getProviderId();
    String getEmail();
    String getName();
    String getProfileImage();
}
