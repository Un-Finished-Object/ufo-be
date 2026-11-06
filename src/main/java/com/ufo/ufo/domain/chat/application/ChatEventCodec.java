package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.domain.ChatEventOutbox;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChatEventCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String encode(ChatSocketEvent<?> event) {
        return mapper.writeValueAsString(event.payload());
    }

    public ChatSocketEvent<?> decode(ChatEventOutbox entry) {
        Object payload = switch (entry.getEventType()) {
            case MESSAGE_CREATED -> mapper.readValue(entry.getEventJson(), ChatMessageCreatedPayload.class);
            case READ_UPDATED -> mapper.readValue(entry.getEventJson(), ChatReadUpdatedPayload.class);
            case ERROR -> throw new IllegalArgumentException("오류 이벤트는 재처리하지 않습니다.");
        };
        return new ChatSocketEvent<>(entry.getEventType(), entry.getRoomId(), payload);
    }
}
