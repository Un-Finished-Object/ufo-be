package com.ufo.ufo.domain.chat.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidChatMessageIdException extends ApiException {
    public InvalidChatMessageIdException() {
        super(HttpStatus.BAD_REQUEST, "messageId는 1 이상이어야 합니다.");
    }
}
