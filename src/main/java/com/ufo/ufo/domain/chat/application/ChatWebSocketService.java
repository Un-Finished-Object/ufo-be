package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketService {

    private final ChatMessageSendService messageSendService;
    private final ChatReadUpdateService readUpdateService;

    public void publishMessage(Principal principal, ChatMessageSendRequest request) {
        try {
            messageSendService.send(principal, request);
        } catch (RuntimeException exception) {
            log.error("채팅 메시지 처리 실패: roomId={}, clientMessageId={}\n{}", request.roomId(),
                    ErrorLogSupport.identifier(request.clientMessageId()), ErrorLogSupport.stackTrace(exception));
            throw exception;
        }
    }

    public void publishReadUpdate(Principal principal, ChatReadUpdateRequest request) {
        try {
            readUpdateService.update(principal, request);
        } catch (RuntimeException exception) {
            log.error("채팅 읽음 처리 실패: roomId={}\n{}", request.roomId(), ErrorLogSupport.stackTrace(exception));
            throw exception;
        }
    }
}
