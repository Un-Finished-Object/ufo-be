package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.ChatOutboxTestConfig;
import com.ufo.ufo.support.fixture.PatternFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
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
@Import({JpaAuditingTestConfig.class, ChatSocketEventPublisher.class, ChatOutboxTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("채팅 성공 이벤트 복구 테스트")
class ChatOutboxRecoveryTest {

    @Autowired
    private ChatSocketEventPublisher publisher;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("커밋 후 전송 실패 시 서버 재시작 후 읽을 수 있는 대기 기록을 남겨야 한다")
    void retainsFailedDeliveryInDatabase() {
        doThrow(new MessageDeliveryException("private-broker-secret"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        Long roomId = new TransactionTemplate(transactionManager).execute(status -> {
            var pattern = PatternFixture.createPattern();
            entityManager.persist(pattern);
            ChatRoom room = ChatRoomFixture.createRoom(pattern);
            entityManager.persist(room);
            publisher.sendMessageCreated(room.getId(), new ChatMessageCreatedPayload(
                    53L, "retry-1", "민트 메리노", "private-message", null, null, LocalDateTime.now()));
            return room.getId();
        });

        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables where lower(table_name) = 'chat_event_outbox'
                """, Integer.class)).as("전송 실패를 복구할 영속 저장소").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from chat_event_outbox where room_id = ? and published_at is null and attempts = 1
                """, Integer.class, roomId)).isEqualTo(1);
    }
}
