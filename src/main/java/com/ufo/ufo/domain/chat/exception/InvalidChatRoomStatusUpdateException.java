package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidChatRoomStatusUpdateException extends ApiException {
    public InvalidChatRoomStatusUpdateException() {
        super(HttpStatus.BAD_REQUEST, "favorites 또는 hidden 중 하나는 필요합니다.");
    }
}
