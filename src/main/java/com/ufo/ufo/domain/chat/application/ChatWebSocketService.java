package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatWebSocketService {

    private final ChatMessageSendService messageSendService;
    private final ChatReadUpdateService readUpdateService;

    public void publishMessage(Principal principal, ChatMessageSendRequest request) {
        messageSendService.send(principal, request);
    }

    public void publishReadUpdate(Principal principal, ChatReadUpdateRequest request) {
        readUpdateService.update(principal, request);
    }
}
