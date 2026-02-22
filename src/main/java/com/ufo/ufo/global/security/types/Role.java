package com.ufo.ufo.global.security.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    ROLE_GUEST("손님"),
    ROLE_USER("회원"),
    ROLE_ADMIN("관리자");

    private final String description;
}
