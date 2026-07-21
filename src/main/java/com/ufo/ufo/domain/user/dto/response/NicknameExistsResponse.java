package com.ufo.ufo.domain.user.dto.response;

public record NicknameExistsResponse(boolean exists) {

    public static NicknameExistsResponse from(boolean exists) {
        return new NicknameExistsResponse(exists);
    }
}
