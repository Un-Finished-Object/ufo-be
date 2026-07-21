package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ChatNicknameNotFoundException extends ApiException {

    public ChatNicknameNotFoundException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "채팅방 닉네임을 찾을 수 없습니다.");
    }
}
