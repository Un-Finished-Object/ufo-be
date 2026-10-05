package com.ufo.ufo.domain.chat.api;

import com.ufo.ufo.domain.chat.application.ChatWebSocketService;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatWebSocketService chatWebSocketService;

    @MessageMapping("/chat/message")
    public void sendMessage(@Payload ChatMessageSendRequest request, Principal principal) {
        chatWebSocketService.publishMessage(principal, request);
    }

    @MessageMapping("/chat/read")
    public void updateRead(@Payload ChatReadUpdateRequest request, Principal principal) {
        chatWebSocketService.publishReadUpdate(principal, request);
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception exception) {
        log.error("채팅 요청 처리 실패\n{}", ErrorLogSupport.stackTrace(exception));
    }
}
