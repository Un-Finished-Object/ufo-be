package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Timeout(45)
@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, ChatReadConcurrencyIntegrationTest.TestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 채팅 읽음 상태 통합 테스트")
class ChatReadConcurrencyIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private ChatRoomRepository roomRepository;
    @Autowired
    private ChatRoomStatusRepository roomStatusRepository;
    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private ChatReadStatusRepository readStatusRepository;
    @Autowired
    private ChatReadUpdateService readUpdateService;
    @Autowired
    private AdminChatService adminChatService;
    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private CapturedEvents capturedEvents;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearEvents() {
        capturedEvents.events.clear();
    }

    @Test
    @DisplayName("이전 메시지 읽음 요청과 이벤트는 최신 읽음 위치를 유지한다")
    void olderRequestPreservesPositionAndEvent() {
        Fixture fixture = fixture(Role.ROLE_USER);
        read(fixture, fixture.latest.getId());
        LocalDateTime readAt = status(fixture).getReadAt();

        read(fixture, fixture.first.getId());

        assertThat(status(fixture).getLastReadMessageId()).isEqualTo(fixture.latest.getId());
        assertThat(status(fixture).getReadAt()).isEqualTo(readAt);
        ChatReadUpdatedPayload event = lastReadEvent();
        assertThat(event.lastReadMessageId()).isEqualTo(fixture.latest.getId());
        assertThat(event.readAt()).isEqualTo(readAt);
        assertThat(chatMessageService.getMessages(fixture.user, fixture.room.getId(), null).lastMessageId())
                .isEqualTo(fixture.latest.getId());
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("다른 채팅방의 메시지를 읽음 처리하지 않는다")
    void rejectsMessageFromAnotherRoom() {
        Fixture fixture = fixture(Role.ROLE_USER);
        Fixture other = fixture(Role.ROLE_USER);

        read(fixture, other.latest.getId());

        assertInvalidRead(fixture);
    }

    @Test
    @DisplayName("존재하지 않는 큰 메시지 ID를 읽음 위치로 저장하지 않는다")
    void rejectsNonexistentMessage() {
        Fixture fixture = fixture(Role.ROLE_USER);

        read(fixture, Long.MAX_VALUE);

        assertInvalidRead(fixture);
    }

    @Test
    @DisplayName("동일 사용자의 동시 최초 읽음은 한 행만 저장하고 최신 위치를 남긴다")
    void simultaneousFirstReadsSaveOneRow() throws Exception {
        Fixture fixture = fixture(Role.ROLE_USER);

        together(() -> read(fixture, fixture.first.getId()), () -> read(fixture, fixture.latest.getId()));

        assertSingleLatestStatus(fixture);
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("기존 읽음 위치를 동시에 갱신해도 마지막 위치가 역행하지 않는다")
    void simultaneousExistingReadsPreserveLatestPosition() throws Exception {
        Fixture fixture = fixture(Role.ROLE_USER);
        read(fixture, fixture.first.getId());

        together(() -> read(fixture, fixture.latest.getId()), () -> read(fixture, fixture.first.getId()));

        assertSingleLatestStatus(fixture);
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("다른 요청의 사용자 잠금이 풀린 뒤 커밋된 최신 읽음 위치를 조회한다")
    void waitingReadSeesCommittedPositionAfterUserLockIsReleased() throws Exception {
        Fixture fixture = fixture(Role.ROLE_ADMIN);
        read(fixture, fixture.first.getId());
        capturedEvents.events.clear();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<?>> waitingRequest = new AtomicReference<>();

        try (var executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(transactionStatus -> {
                userRepository.findByIdForUpdate(fixture.user.getId()).orElseThrow();
                check(fixture, fixture.latest.getId());
                waitingRequest.set(executor.submit(() -> {
                    started.countDown();
                    read(fixture, fixture.first.getId());
                }));
                assertThatCode(() -> assertThat(started.await(10, TimeUnit.SECONDS)).isTrue())
                        .doesNotThrowAnyException();
                assertThatThrownBy(() -> waitingRequest.get().get(1, TimeUnit.SECONDS))
                        .isInstanceOf(TimeoutException.class);
            });
            waitingRequest.get().get(30, TimeUnit.SECONDS);
        }

        assertSingleLatestStatus(fixture);
        assertThat(lastReadEvent().lastReadMessageId()).isEqualTo(fixture.latest.getId());
        assertThat(lastReadEvent().readAt()).isEqualTo(status(fixture).getReadAt());
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("관리자의 동시 최초 확인도 한 행과 최신 읽음 위치를 남긴다")
    void simultaneousAdminChecksSaveOneRow() throws Exception {
        Fixture fixture = fixture(Role.ROLE_ADMIN);

        together(() -> check(fixture, fixture.first.getId()), () -> check(fixture, fixture.latest.getId()));

        assertSingleLatestStatus(fixture);
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("관리자 API와 WebSocket의 동시 요청은 같은 읽음 상태를 갱신한다")
    void adminAndSocketShareReadPosition() throws Exception {
        Fixture fixture = fixture(Role.ROLE_ADMIN);

        together(() -> check(fixture, fixture.latest.getId()), () -> read(fixture, fixture.first.getId()));

        assertSingleLatestStatus(fixture);
    }

    @Test
    @DisplayName("관리자의 이전 메시지 확인은 최신 읽음 위치와 시각을 바꾸지 않는다")
    void olderAdminCheckPreservesPositionAndReadAt() {
        Fixture fixture = fixture(Role.ROLE_ADMIN);
        check(fixture, fixture.latest.getId());
        LocalDateTime readAt = status(fixture).getReadAt();

        check(fixture, fixture.first.getId());

        assertThat(status(fixture).getLastReadMessageId()).isEqualTo(fixture.latest.getId());
        assertThat(status(fixture).getReadAt()).isEqualTo(readAt);
        assertNoUnread(fixture);
    }

    @Test
    @DisplayName("관리자도 다른 채팅방 메시지를 확인할 수 없다")
    void adminRejectsMessageFromAnotherRoom() {
        Fixture fixture = fixture(Role.ROLE_ADMIN);
        Fixture other = fixture(Role.ROLE_USER);

        assertThatThrownBy(() -> check(fixture, other.latest.getId()))
                .isInstanceOf(InvalidChatMessageIdException.class);
        assertThat(readStatusRepository.findByRoom_IdAndUser_Id(fixture.room.getId(), fixture.user.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("사용자와 채팅방이 같은 읽음 행은 DB 제약으로 중복 저장을 거절한다")
    void databaseRejectsDuplicateReadRows() {
        Fixture fixture = fixture(Role.ROLE_USER);
        readStatusRepository.saveAndFlush(newStatus(fixture));

        assertThatThrownBy(() -> readStatusRepository.saveAndFlush(newStatus(fixture)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("읽음 처리 이후 트랜잭션이 실패하면 읽음 상태도 롤백한다")
    void rollbackDoesNotPersistReadPosition() {
        Fixture fixture = fixture(Role.ROLE_ADMIN);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            check(fixture, fixture.latest.getId());
            throw new IllegalStateException("읽음 처리 후 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(readStatusRepository.findByRoom_IdAndUser_Id(fixture.room.getId(), fixture.user.getId()))
                .isEmpty();
    }

    private Fixture fixture(Role role) {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.saveAndFlush(User.builder()
                .email(identifier + "@example.com").nickname(identifier.substring(0, 16)).role(role).build());
        ChatRoom room = roomRepository.saveAndFlush(
                ChatRoomFixture.createRoom(patternRepository.saveAndFlush(PatternFixture.createPattern())));
        roomStatusRepository.saveAndFlush(ChatRoomStatus.builder()
                .room(room).user(user).nickname("레드 코튼").build());
        ChatMessage first = messageRepository.saveAndFlush(
                ChatMessage.builder().room(room).user(user).text("첫 메시지").build());
        ChatMessage latest = messageRepository.saveAndFlush(
                ChatMessage.builder().room(room).user(user).text("마지막 메시지").build());
        return new Fixture(user, room, first, latest);
    }

    private void read(Fixture fixture, Long messageId) {
        readUpdateService.update(() -> fixture.user.getEmail(),
                new ChatReadUpdateRequest(fixture.room.getId(), messageId));
    }

    private void check(Fixture fixture, Long messageId) {
        adminChatService.checkMessage(fixture.user, fixture.room.getId(), messageId);
    }

    private ChatReadStatus status(Fixture fixture) {
        return readStatusRepository.findByRoom_IdAndUser_Id(fixture.room.getId(), fixture.user.getId()).orElseThrow();
    }

    private ChatReadStatus newStatus(Fixture fixture) {
        return ChatReadStatus.builder().room(fixture.room).user(fixture.user)
                .lastReadMessageId(fixture.first.getId()).readAt(LocalDateTime.now()).build();
    }

    private void assertSingleLatestStatus(Fixture fixture) {
        assertThat(readStatusRepository.findAll().stream()
                .filter(status -> status.getRoom().getId().equals(fixture.room.getId())
                        && status.getUser().getId().equals(fixture.user.getId()))).hasSize(1);
        assertThat(status(fixture).getLastReadMessageId()).isEqualTo(fixture.latest.getId());
    }

    private void assertNoUnread(Fixture fixture) {
        assertThat(messageRepository.countUnreadByRoomIds(fixture.user.getId(), List.of(fixture.room.getId())))
                .isEmpty();
    }

    private void assertInvalidRead(Fixture fixture) {
        assertThat(readStatusRepository.findByRoom_IdAndUser_Id(fixture.room.getId(), fixture.user.getId()))
                .isEmpty();
        assertThat(capturedEvents.events).hasSize(1);
        ChatSocketEvent<?> event = capturedEvents.events.element();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(((ChatErrorPayload) event.payload()).code()).isEqualTo("INVALID_LAST_READ_MESSAGE_ID");
    }

    private ChatReadUpdatedPayload lastReadEvent() {
        return capturedEvents.events.stream().filter(event -> event.eventType() == ChatEventType.READ_UPDATED)
                .map(event -> (ChatReadUpdatedPayload) event.payload()).toList().getLast();
    }

    private void together(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> runReady(ready, first));
            var secondResult = executor.submit(() -> runReady(ready, second));
            firstResult.get(30, TimeUnit.SECONDS);
            secondResult.get(30, TimeUnit.SECONDS);
        }
    }

    private void runReady(CountDownLatch ready, Runnable action) {
        ready.countDown();
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 준비 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        action.run();
    }

    private record Fixture(User user, ChatRoom room, ChatMessage first, ChatMessage latest) {
    }

    static class CapturedEvents {
        private final ConcurrentLinkedQueue<ChatSocketEvent<?>> events = new ConcurrentLinkedQueue<>();

        SimpMessagingTemplate messagingTemplate() {
            ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
            channel.subscribe(message -> events.add((ChatSocketEvent<?>) message.getPayload()));
            return new SimpMessagingTemplate(channel);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({UserService.class, ChatReadUpdateService.class, ChatReadStatusService.class, AdminChatService.class,
            ChatMessageService.class, ChatSocketAccessService.class, ChatSocketEventPublisher.class})
    static class TestConfig {

        @Bean
        ImageService imageService() {
            return mock(ImageService.class);
        }

        @Bean
        CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }

        @Bean
        SimpMessagingTemplate messagingTemplate(CapturedEvents events) {
            return events.messagingTemplate();
        }
    }
}
