package com.ufo.ufo.domain.image.domain;

import com.ufo.ufo.domain.image.exception.InvalidImagePurposeException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ImagePurpose {
    STYLE("styles"),
    PROFILE("profiles"),
    PATTERN("patterns");

    private final String prefix;

    public String prefix() {
        return prefix;
    }

    public static ImagePurpose from(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new InvalidImagePurposeException();
        }
        try {
            return ImagePurpose.valueOf(purpose.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidImagePurposeException();
        }
    }
}
