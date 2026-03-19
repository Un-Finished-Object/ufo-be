package com.ufo.ufo.domain.user.dto.response;

import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record UserResponse(
        Long userId,
        String email,
        String nickname,
        String profileImage,
        int joinDate
) {
    public static UserResponse from(User user) {
        int joinDate = 0;
        if (user.getCreatedAt() != null) {
            joinDate = Math.toIntExact(
                    ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now())
            );
        }
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                joinDate
        );
    }
}
