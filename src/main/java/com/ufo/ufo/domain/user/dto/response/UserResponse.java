package com.ufo.ufo.domain.user.dto.response;

import com.ufo.ufo.domain.user.domain.User;

public record UserResponse(
        String email,
        String nickname,
        String profileImage
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage()
        );
    }
}
