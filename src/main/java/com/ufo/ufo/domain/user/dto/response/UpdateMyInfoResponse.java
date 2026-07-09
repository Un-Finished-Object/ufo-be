package com.ufo.ufo.domain.user.dto.response;

import com.ufo.ufo.domain.user.domain.User;

public record UpdateMyInfoResponse(
        Long userId,
        String userName,
        String profileImage
) {
    public static UpdateMyInfoResponse from(User user, String profileImageUrl) {
        return new UpdateMyInfoResponse(
                user.getId(),
                user.getNickname(),
                profileImageUrl
        );
    }
}
