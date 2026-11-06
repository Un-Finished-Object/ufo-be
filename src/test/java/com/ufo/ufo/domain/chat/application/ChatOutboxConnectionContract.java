package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.ufo.ufo.domain.chat.dao.ChatEventOutboxRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class ChatOutboxConnectionContract {

    @Autowired
    private ChatSocketEventPublisher publisher;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ChatEventOutboxRepository repository;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("DB 연결이 하나여도 커밋 후 다른 스레드에서 이벤트를 전송해야 한다")
    void dispatchesAfterProducerReleasesOnlyConnection(ChatEventType type) throws Exception {
        var delivered = new CountDownLatch(1);
        var deliveryThread = new AtomicReference<Thread>();
        Thread producerThread = Thread.currentThread();
        doAnswer(invocation -> {
            deliveryThread.set(Thread.currentThread());
            delivered.countDown();
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        Long roomId = new TransactionTemplate(transactionManager).execute(status -> {
            String identifier = UUID.randomUUID().toString().replace("-", "");
            var user = UserFixture.createUser(identifier + "@example.test", Role.ROLE_USER);
            user.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
            entityManager.persist(user);
            var pattern = PatternFixture.createPattern();
            entityManager.persist(pattern);
            ChatRoom room = ChatRoomFixture.createRoom(pattern);
            entityManager.persist(room);
            ChatMessage message = ChatMessage.builder().room(room).user(user).text("연결 풀 검증").build();
            entityManager.persist(message);
            if (type == ChatEventType.MESSAGE_CREATED) {
                publisher.sendMessageCreated(room.getId(), new ChatMessageCreatedPayload(
                        message.getId(), "pool-test", "민트 메리노", message.getText(), null, null,
                        message.getCreatedAt()));
            } else {
                LocalDateTime readAt = LocalDateTime.now();
                entityManager.persist(ChatReadStatus.builder().room(room).user(user)
                        .lastReadMessageId(message.getId()).readAt(readAt).build());
                publisher.sendReadUpdated(room.getId(), new ChatReadUpdatedPayload(
                        user.getId(), message.getId(), readAt));
            }
            return room.getId();
        });

        assertThat(delivered.await(5, TimeUnit.SECONDS)).as("연결 풀 대기 없이 커밋 후 전송").isTrue();
        assertThat(deliveryThread.get()).isNotSameAs(producerThread);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findAll().stream().filter(entry -> entry.getRoomId().equals(roomId)))
                        .singleElement().satisfies(entry -> assertThat(entry.getPublishedAt()).isNotNull()));
    }
}
