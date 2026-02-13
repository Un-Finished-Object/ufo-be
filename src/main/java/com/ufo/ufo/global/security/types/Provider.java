package com.ufo.ufo.global.security.types;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.ufo.ufo.global.exception.UnsupportedProviderException;

@Getter
@RequiredArgsConstructor
public enum Provider {
    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver");

    private final String registrationId;

    public static Provider from(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equalsIgnoreCase(registrationId))
                .findFirst()
                .orElseThrow(() -> new UnsupportedProviderException(registrationId));
    }
}
