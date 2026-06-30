package com.ufo.ufo.domain.user.dto.response;

import com.ufo.ufo.domain.user.domain.User;

public record UpdateMyInfoResponse(
        Long userId,
        String nickname,
        String profileImage
) {
    public static UpdateMyInfoResponse from(User user) {
        return new UpdateMyInfoResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImage()
        );
    }
}
