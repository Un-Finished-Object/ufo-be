package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.lang.reflect.Field;
import java.time.LocalDateTime;

public final class ChatRoomFixture {

    private ChatRoomFixture() {
    }

    public static ChatRoom createRoom(Pattern pattern) {
        return ChatRoom.builder()
                .pattern(pattern)
                .segmentStartAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .segmentEndAt(LocalDateTime.of(2026, 1, 8, 0, 0))
                .build();
    }

    public static ChatRoom createRoomWithId(Pattern pattern, Long roomId) {
        ChatRoom room = createRoom(pattern);
        setId(room, roomId);
        return room;
    }

    public static void setId(ChatRoom room, Long roomId) {
        try {
            Field idField = ChatRoom.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(room, roomId);

            Field createdAtField = ChatRoom.class.getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(room, LocalDateTime.now());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
