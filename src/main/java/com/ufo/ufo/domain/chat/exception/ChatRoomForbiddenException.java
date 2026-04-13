package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ChatRoomForbiddenException extends ApiException {
    public ChatRoomForbiddenException() {
        super(HttpStatus.FORBIDDEN, "접근 권한이 없는 채팅방입니다.");
    }
}
