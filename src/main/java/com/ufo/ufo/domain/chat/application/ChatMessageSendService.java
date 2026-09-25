package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatSocketAccessService accessService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSocketEventPublisher eventPublisher;

    @Transactional
    public void send(Principal principal, ChatMessageSendRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }

        String clientMessageId = request.clientMessageId();
        Optional<User> maybeUser = accessService.resolveUser(principal, roomId, clientMessageId);
        if (maybeUser.isEmpty()) {
            return;
        }

        Optional<ChatRoom> maybeRoom = accessService.resolveRoom(roomId, clientMessageId);
        if (maybeRoom.isEmpty()) {
            return;
        }

        User sender = maybeUser.get();
        Optional<ChatRoomStatus> maybeRoomStatus = accessService.resolveRoomStatus(
                sender.getId(), roomId, clientMessageId
        );
        if (maybeRoomStatus.isEmpty()) {
            return;
        }

        String text = normalizeText(request.text());
        if (text == null) {
            eventPublisher.sendError(roomId, "INVALID_MESSAGE_TEXT", "메시지 내용은 비어 있을 수 없습니다.", clientMessageId);
            return;
        }

        Optional<ChatMessage> maybeReplyMessage = resolveReplyMessage(request, roomId, clientMessageId);
        if (request.replyRequested() && maybeReplyMessage.isEmpty()) {
            return;
        }

        ChatMessage savedMessage = chatMessageRepository.save(
                ChatMessage.builder()
                        .room(maybeRoom.get())
                        .user(sender)
                        .text(text)
                        .replyMessage(maybeReplyMessage.orElse(null))
                        .build()
        );

        ChatMessage replyMessage = savedMessage.getReplyMessage();
        ChatMessageCreatedPayload payload = new ChatMessageCreatedPayload(
                savedMessage.getId(),
                clientMessageId,
                maybeRoomStatus.get().getNickname(),
                text,
                replyMessage == null ? null : accessService.getChatNickname(replyMessage.getUser().getId(), roomId),
                replyMessage == null ? null : replyMessage.getId(),
                savedMessage.getCreatedAt()
        );
        eventPublisher.sendMessageCreated(roomId, payload);
    }

    private Optional<ChatMessage> resolveReplyMessage(
            ChatMessageSendRequest request,
            Long roomId,
            String clientMessageId
    ) {
        if (!request.replyRequested()) {
            return Optional.empty();
        }

        Long replyMessageId = request.replyMessageId();
        if (replyMessageId == null || replyMessageId <= 0) {
            eventPublisher.sendError(roomId, "CHAT_REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다.", clientMessageId);
            return Optional.empty();
        }

        Optional<ChatMessage> maybeReplyMessage = chatMessageRepository.findByIdAndRoom_Id(replyMessageId, roomId);
        if (maybeReplyMessage.isEmpty()) {
            eventPublisher.sendError(roomId, "CHAT_REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다.", clientMessageId);
        }
        return maybeReplyMessage;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
