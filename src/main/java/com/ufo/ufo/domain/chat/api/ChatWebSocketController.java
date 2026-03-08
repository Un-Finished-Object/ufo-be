package com.ufo.ufo.domain.chat.api;

import com.ufo.ufo.domain.chat.application.ChatWebSocketService;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
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
}
