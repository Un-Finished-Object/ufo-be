package com.ufo.ufo.domain.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatMessagesResponse(
        @JsonProperty("lastmessageId")
        Long lastMessageId,
        Boolean hasNext,
        Long nextMessageId,
        List<ChatMessageItemResponse> messages
) {
    public static ChatMessagesResponse of(
            Long lastMessageId,
            Boolean hasNext,
            Long nextMessageId,
            List<ChatMessageItemResponse> messages
    ) {
        return new ChatMessagesResponse(lastMessageId, hasNext, nextMessageId, messages);
    }
}
