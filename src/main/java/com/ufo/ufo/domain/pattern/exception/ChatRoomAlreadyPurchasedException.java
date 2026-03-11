package com.ufo.ufo.domain.pattern.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ChatRoomAlreadyPurchasedException extends ApiException {
    public ChatRoomAlreadyPurchasedException() {
        super(HttpStatus.BAD_REQUEST, "이미 구매한 채팅방입니다.");
    }
}
