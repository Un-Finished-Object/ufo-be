package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ChatNicknameGenerationException extends ApiException {

    public ChatNicknameGenerationException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "사용 가능한 채팅방 닉네임이 없습니다.");
    }
}
