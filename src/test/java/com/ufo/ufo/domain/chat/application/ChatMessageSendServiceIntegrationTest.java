package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, ChatMessageSendService.class,
        ChatSocketAccessService.class, ChatSocketEventPublisher.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("채팅 메시지 전송 서비스 통합 테스트")
class ChatMessageSendServiceIntegrationTest {

    @Autowired
    private ChatMessageSendService messageSendService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Long roomId;
    private String email;

    @BeforeEach
    void setUp() {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        email = identifier + "@example.com";
        roomId = new TransactionTemplate(transactionManager).execute(status -> {
            User user = UserFixture.createUser(email, Role.ROLE_USER);
            user.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
            entityManager.persist(user);
            Pattern pattern = PatternFixture.createPattern();
            entityManager.persist(pattern);
            ChatRoom room = ChatRoomFixture.createRoom(pattern);
            entityManager.persist(room);
            entityManager.persist(ChatRoomStatus.builder().user(user).room(room).nickname("민트 메리노").build());
            return room.getId();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "한", "😀"})
    @DisplayName("1,000자 메시지는 커밋한 뒤 전송하고 별도 연결에서 조회할 수 있어야 한다")
    void send_WithMaximumLength_PersistsBeforeDelivery(String character) throws Exception {
        String text = character.repeat(1000);
        AtomicReference<List<String>> committedTexts = new AtomicReference<>();
        doAnswer(invocation -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "select text from chat_messages where chat_room_id = ?")) {
                statement.setLong(1, roomId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<String> texts = new ArrayList<>();
                    while (rows.next()) {
                        texts.add(rows.getString(1));
                    }
                    committedTexts.set(texts);
                }
            }
            return null;
        }).when(messagingTemplate).convertAndSend(eq(destination()), any(Object.class));

        messageSendService.send(() -> email,
                new ChatMessageSendRequest(roomId, " \t" + text + "\n ", "length-valid", false, null));

        assertThat(committedTexts.get()).containsExactly(text);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination()), sent.capture());
        ChatSocketEvent<?> event = (ChatSocketEvent<?>) sent.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.MESSAGE_CREATED);
        assertThat(event.roomId()).isEqualTo(roomId);
        ChatMessageCreatedPayload payload = (ChatMessageCreatedPayload) event.payload();
        assertThat(payload.messageId()).isNotNull();
        assertThat(payload.createdAt()).isNotNull();
        assertThat(payload.text()).isEqualTo(text);
        assertThat(payload.clientMessageId()).isEqualTo("length-valid");
        assertThat(storedMessages()).isEqualTo(1);
    }

    @Test
    @DisplayName("1,000자를 넘는 메시지는 DB에 남기지 않고 요청 식별자와 함께 오류를 반환해야 한다")
    void send_ExceedingLengthLimit_RejectsWithoutPersisting() {
        messageSendService.send(() -> email,
                new ChatMessageSendRequest(roomId, " " + "😀".repeat(1001) + " ",
                        "length-invalid", false, null));

        assertThat(storedMessages()).isZero();
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination()), sent.capture());
        ChatSocketEvent<?> event = (ChatSocketEvent<?>) sent.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.payload()).isEqualTo(new ChatErrorPayload(
                "INVALID_MESSAGE_TEXT", "메시지는 1,000자까지 입력할 수 있습니다.", "length-invalid"));
    }

    @Test
    @DisplayName("길이 검증을 통과해도 트랜잭션이 롤백되면 메시지와 성공 이벤트가 남지 않아야 한다")
    void send_WhenRolledBack_DiscardsMessageAndEvent() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            messageSendService.send(() -> email,
                    new ChatMessageSendRequest(roomId, "한".repeat(1000), "length-rollback", false, null));
            verifyNoInteractions(messagingTemplate);
            status.setRollbackOnly();
        });

        assertThat(storedMessages()).isZero();
        verifyNoInteractions(messagingTemplate);
    }

    private String destination() {
        return "/sub/chat/rooms/" + roomId;
    }

    private int storedMessages() {
        return jdbcTemplate.queryForObject(
                "select count(*) from chat_messages where chat_room_id = ?", Integer.class, roomId);
    }
}
