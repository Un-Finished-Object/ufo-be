package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ChatRoomNotFoundException extends ApiException {
    public ChatRoomNotFoundException() {
        super(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다.");
    }
}
