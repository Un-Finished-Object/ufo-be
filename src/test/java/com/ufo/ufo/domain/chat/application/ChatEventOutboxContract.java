package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ufo.ufo.domain.chat.dao.ChatEventOutboxRepository;
import com.ufo.ufo.domain.chat.domain.ChatEventOutbox;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.logging.LogCapture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class ChatEventOutboxContract {

    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Autowired
    private ChatSocketEventPublisher publisher;
    @Autowired
    private ChatEventOutboxRepository repository;
    @Autowired
    private ChatEventOutboxProcessor processor;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoSpyBean
    private ChatEventOutboxDispatcher dispatcher;

    private User user;
    private ChatRoom room;
    private Long messageId;

    @BeforeEach
    void prepareRoom() {
        repository.deleteAllInBatch();
        messageId = null;
        transaction().executeWithoutResult(status -> {
            String identifier = UUID.randomUUID().toString().replace("-", "");
            user = UserFixture.createUser(identifier + "@example.test", Role.ROLE_USER);
            user.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
            entityManager.persist(user);
            var pattern = PatternFixture.createPattern();
            entityManager.persist(pattern);
            room = ChatRoomFixture.createRoom(pattern);
            entityManager.persist(room);
        });
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("성공 이벤트는 업무 데이터와 함께 저장되고 커밋 후 같은 응답으로 전송해야 한다")
    void sendsSnapshotAfterCommit(ChatEventType type) {
        AtomicInteger visibleRows = new AtomicInteger();
        doAnswer(invocation -> {
            visibleRows.set(jdbc.queryForObject(
                    "select count(*) from chat_event_outbox where room_id = ?", Integer.class, room.getId()));
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        transaction().executeWithoutResult(status -> {
            publishState(type);
            verifyNoInteractions(messagingTemplate);
        });

        assertThat(visibleRows.get()).isEqualTo(1);
        ChatEventOutbox entry = repository.findAll().getFirst();
        assertThat(entry.getPublishedAt()).isNotNull();
        assertThat(entry.getAttempts()).isEqualTo(1);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(destination()), sent.capture());
        ChatSocketEvent<?> event = (ChatSocketEvent<?>) sent.getValue();
        assertThat(event.eventType()).isEqualTo(type);
        assertThat(event.roomId()).isEqualTo(room.getId());
        assertThat(event.payload()).isEqualTo(payload(type));
        assertThat(storedState(type)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("업무 트랜잭션 롤백 시 상태와 전송 기록이 함께 취소돼야 한다")
    void rollbackDiscardsStateAndOutbox(ChatEventType type) {
        transaction().executeWithoutResult(status -> {
            publishState(type);
            status.setRollbackOnly();
        });

        assertThat(storedState(type)).isZero();
        assertThat(repository.count()).isZero();
        verifyNoInteractions(messagingTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("커밋 단계 실패 시 업무 상태와 전송 기록을 남기지 않아야 한다")
    void commitFailureDiscardsStateAndOutbox(ChatEventType type) {
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            publishState(type);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("injected-commit-failure");
                }
            });
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedState(type)).isZero();
        assertThat(repository.count()).isZero();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("트랜잭션 없는 성공 이벤트는 저장하거나 전송하지 않아야 한다")
    void ignoresSuccessWithoutTransaction() {
        publish(ChatEventType.MESSAGE_CREATED);

        assertThat(repository.count()).isZero();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("오류 이벤트는 롤백과 무관하게 즉시 전송하고 재처리하지 않아야 한다")
    void sendsErrorImmediately() {
        transaction().executeWithoutResult(status -> {
            publisher.sendError(room.getId(), "INVALID_MESSAGE_TEXT", "오류 내용", "message-123");
            status.setRollbackOnly();
        });

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(destination()), sent.capture());
        assertThat(((ChatSocketEvent<?>) sent.getValue()).payload())
                .isEqualTo(new ChatErrorPayload("INVALID_MESSAGE_TEXT", "오류 내용", "message-123"));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("커밋 직후 실행하지 못한 이벤트는 새 실행기가 DB에서 읽어 복구해야 한다")
    void newRunnerRecoversCommittedEvent() {
        suppressImmediateDelivery();
        transaction().executeWithoutResult(status -> publishState(ChatEventType.MESSAGE_CREATED));

        assertThat(repository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getAttempts()).isZero();
            assertThat(entry.getPublishedAt()).isNull();
        });
        verifyNoInteractions(messagingTemplate);

        newRunner().retryPending();

        assertThat(repository.findAll().getFirst().getPublishedAt()).isNotNull();
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("전송 실패는 상태를 유지하고 대기 시간이 지난 뒤 같은 스냅샷으로 재시도해야 한다")
    void retriesFailedSnapshotWithoutLeakingMessage(ChatEventType type) {
        doThrow(new MessageDeliveryException("private-broker-secret")).doNothing()
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        try (var logs = new LogCapture(ChatEventOutboxProcessor.class)) {
            transaction().executeWithoutResult(status -> publishState(type));
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.events().getFirst().getFormattedMessage())
                    .contains(room.getId().toString(), type.name(), "MessageDeliveryException")
                    .doesNotContain("private-message", "private-broker-secret");
        }
        ChatEventOutbox failed = repository.findAll().getFirst();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(storedState(type)).isEqualTo(1);
        newRunner().retryPending();
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        makeDue();
        newRunner().retryPending();

        ChatEventOutbox delivered = repository.findAll().getFirst();
        assertThat(delivered.getAttempts()).isEqualTo(2);
        assertThat(delivered.getPublishedAt()).isNotNull();
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), sent.capture());
        assertThat(sent.getAllValues().get(0)).isEqualTo(sent.getAllValues().get(1));
    }

    @Test
    @DisplayName("실패한 이벤트가 같은 방의 후속 이벤트를 막지만 다른 방은 전송해야 한다")
    void failedHeadBlocksOnlyItsRoom() {
        doThrow(new MessageDeliveryException("failure")).doNothing()
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        transaction().executeWithoutResult(status -> publish(ChatEventType.MESSAGE_CREATED));
        transaction().executeWithoutResult(status -> publish(ChatEventType.READ_UPDATED));
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        Long otherRoomId = transaction().execute(status -> {
            var pattern = PatternFixture.createPattern();
            entityManager.persist(pattern);
            ChatRoom other = ChatRoomFixture.createRoom(pattern);
            entityManager.persist(other);
            publisher.sendReadUpdated(other.getId(), new ChatReadUpdatedPayload(user.getId(), 99L, EVENT_TIME));
            return other.getId();
        });
        assertThat(repository.findAll().stream().filter(e -> e.getRoomId().equals(otherRoomId)))
                .singleElement().satisfies(e -> assertThat(e.getPublishedAt()).isNotNull());
        makeDue();
        newRunner().retryPending();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(4)).convertAndSend(anyString(), sent.capture());
        assertThat(sent.getAllValues().stream().map(e -> ((ChatSocketEvent<?>) e).eventType()).toList())
                .containsExactly(ChatEventType.MESSAGE_CREATED, ChatEventType.READ_UPDATED,
                        ChatEventType.MESSAGE_CREATED, ChatEventType.READ_UPDATED);
        assertThat(repository.findAll()).allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
    }

    @Test
    @Timeout(30)
    @DisplayName("동일 방을 처리하는 두 작업자는 동시에 같은 이벤트를 발행하지 않아야 한다")
    void concurrentWorkersSerializeByRoom() throws Exception {
        suppressImmediateDelivery();
        transaction().executeWithoutResult(status -> publish(ChatEventType.MESSAGE_CREATED));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processor.processRoom(room.getId()));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    processor.processRoom(room.getId());
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }

        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        assertThat(repository.findAll().getFirst().getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("브로커 수락 후 전송 상태 커밋이 실패하면 같은 이벤트가 중복될 수 있어야 한다")
    void ambiguousDeliveryIsRetriedFromPersistedState() {
        suppressImmediateDelivery();
        transaction().executeWithoutResult(status -> publishState(ChatEventType.MESSAGE_CREATED));
        doAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("injected-delivery-commit-failure");
                }
            });
            return null;
        }).doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> newRunner().retryPending()).doesNotThrowAnyException();
        assertThat(repository.findAll().getFirst().getPublishedAt()).isNull();
        assertThat(storedState(ChatEventType.MESSAGE_CREATED)).isEqualTo(1);
        newRunner().retryPending();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), sent.capture());
        assertThat(sent.getAllValues().get(0)).isEqualTo(sent.getAllValues().get(1));
        assertThat(repository.findAll().getFirst().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("정리는 7일 지난 성공 기록만 삭제하고 미처리 이벤트는 유지해야 한다")
    void cleanupRetainsPendingEvents() {
        transaction().executeWithoutResult(status -> publish(ChatEventType.MESSAGE_CREATED));
        jdbc.update("update chat_event_outbox set published_at = ?", LocalDateTime.now().minusDays(8));
        suppressImmediateDelivery();
        transaction().executeWithoutResult(status -> publish(ChatEventType.READ_UPDATED));

        newRunner().deletePublished();

        assertThat(repository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getEventType()).isEqualTo(ChatEventType.READ_UPDATED);
            assertThat(entry.getPublishedAt()).isNull();
        });
    }

    private void publishState(ChatEventType type) {
        if (type == ChatEventType.MESSAGE_CREATED) {
            ChatMessage message = ChatMessage.builder().room(room).user(user).text("private-message").build();
            entityManager.persist(message);
            messageId = message.getId();
        } else {
            entityManager.persist(ChatReadStatus.builder().room(room).user(user)
                    .lastReadMessageId(53L).readAt(EVENT_TIME).build());
        }
        publish(type);
    }

    private void publish(ChatEventType type) {
        if (type == ChatEventType.MESSAGE_CREATED) {
            if (messageId == null
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                ChatMessage message = ChatMessage.builder().room(room).user(user).text("private-message").build();
                entityManager.persist(message);
                messageId = message.getId();
            }
            publisher.sendMessageCreated(room.getId(), (ChatMessageCreatedPayload) payload(type));
        } else {
            publisher.sendReadUpdated(room.getId(), (ChatReadUpdatedPayload) payload(type));
        }
    }

    private Object payload(ChatEventType type) {
        return type == ChatEventType.MESSAGE_CREATED
                ? new ChatMessageCreatedPayload(messageId, "message-123", "민트 메리노", "private-message",
                        "답장 작성자", 52L, EVENT_TIME)
                : new ChatReadUpdatedPayload(user.getId(), 53L, EVENT_TIME);
    }

    private int storedState(ChatEventType type) {
        String table = type == ChatEventType.MESSAGE_CREATED ? "chat_messages" : "chat_read_statuses";
        return jdbc.queryForObject("select count(*) from " + table + " where chat_room_id = ?",
                Integer.class, room.getId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private String destination() {
        return "/sub/chat/rooms/" + room.getId();
    }

    private void suppressImmediateDelivery() {
        doNothing().when(dispatcher).dispatch(any(ChatSocketEvent.class));
    }

    private ChatEventOutboxTaskRunner newRunner() {
        return new ChatEventOutboxTaskRunner(repository, dispatcher, processor);
    }

    private void makeDue() {
        jdbc.update("update chat_event_outbox set next_attempt_at = ? where published_at is null",
                LocalDateTime.now().minusMinutes(1));
    }
}
