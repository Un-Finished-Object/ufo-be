package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.support.logging.LogCapture;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(ChatSocketEventTransactionTest.TestConfig.class)
@DisplayName("채팅 성공 이벤트의 커밋 후 전송 테스트")
class ChatSocketEventTransactionTest {

    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private ChatSocketEventPublisher publisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CapturedMessages messages;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("create table if not exists chat_event_test (id bigint primary key)");
        jdbcTemplate.update("delete from chat_event_test");
        messages.clear();
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("성공 이벤트는 커밋 후 한 번 전송되고 수신 시 별도 연결에서 저장 내용을 조회할 수 있다")
    void sendsSuccessOnlyAfterCommit(ChatEventType eventType) {
        transaction().executeWithoutResult(status -> {
            saveState();
            publishSuccess(eventType);

            assertThat(messages.events).isEmpty();
        });

        assertThat(messages.events).hasSize(1);
        assertThat(messages.destinations).containsExactly("/sub/chat/rooms/10");
        assertThat(messages.visibleRows).containsExactly(1);
        ChatSocketEvent<?> event = messages.events.getFirst();
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.roomId()).isEqualTo(10L);
        if (eventType == ChatEventType.MESSAGE_CREATED) {
            assertThat(event.payload()).isEqualTo(new ChatMessageCreatedPayload(
                    53L, "message-123", "민트 메리노", "private-message", null, null, EVENT_TIME));
        } else {
            assertThat(event.payload()).isEqualTo(new ChatReadUpdatedPayload(21L, 53L, EVENT_TIME));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("저장 이후 요청이 실패해 롤백되면 성공 이벤트를 전송하지 않는다")
    void rollbackDoesNotSendSuccess(ChatEventType eventType) {
        var failure = new IllegalStateException("저장 이후 실패");

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            saveState();
            publishSuccess(eventType);
            throw failure;
        })).isSameAs(failure);

        assertThat(messages.events).isEmpty();
        assertThat(storedRows()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("커밋 단계에서 실패하면 저장 내용과 성공 이벤트가 남지 않는다")
    void commitFailureDoesNotSendSuccess(ChatEventType eventType) {
        var failure = new IllegalStateException("커밋 단계 실패");

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            saveState();
            publishSuccess(eventType);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw failure;
                }
            });
        })).isSameAs(failure);

        assertThat(messages.events).isEmpty();
        assertThat(storedRows()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("JDBC commit 호출이 실패하면 성공 이벤트를 전송하지 않는다")
    void jdbcCommitFailureDoesNotSendSuccess(ChatEventType eventType) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Connection failingConnection = spy(connection);
            doAnswer(invocation -> {
                // DB가 커밋을 거절한 상황을 재현하고 연결 정리 시 자동 커밋되지 않도록 한다.
                failingConnection.rollback();
                throw new SQLException("injected-jdbc-commit-failure");
            }).doCallRealMethod().when(failingConnection).commit();
            var failingDataSource = new SingleConnectionDataSource(failingConnection, true);
            var failingJdbc = new JdbcTemplate(failingDataSource);
            var failingTransaction = new TransactionTemplate(new DataSourceTransactionManager(failingDataSource));

            assertThatThrownBy(() -> failingTransaction.executeWithoutResult(status -> {
                failingJdbc.update("insert into chat_event_test (id) values (53)");
                publishSuccess(eventType);
            })).isInstanceOf(TransactionSystemException.class)
                    .hasRootCauseMessage("injected-jdbc-commit-failure");

            assertThat(messages.events).isEmpty();
            assertThat(storedRows()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(value = ChatEventType.class, names = {"MESSAGE_CREATED", "READ_UPDATED"})
    @DisplayName("트랜잭션 없이 발행한 성공 이벤트는 저장 완료로 전송하지 않는다")
    void doesNotSendSuccessWithoutTransaction(ChatEventType eventType) {
        publishSuccess(eventType);

        assertThat(messages.events).isEmpty();
    }

    @Test
    @DisplayName("입력 거부 오류는 커밋을 기다리지 않고 전송하며 롤백 여부에 영향을 받지 않는다")
    void sendsValidationErrorImmediately() {
        transaction().executeWithoutResult(status -> {
            publisher.sendError(10L, "INVALID_MESSAGE_TEXT", "메시지 내용 오류", "message-123");

            assertThat(messages.events).hasSize(1);
            assertThat(messages.events.getFirst().eventType()).isEqualTo(ChatEventType.ERROR);
            assertThat(messages.events.getFirst().payload())
                    .isEqualTo(new ChatErrorPayload("INVALID_MESSAGE_TEXT", "메시지 내용 오류", "message-123"));
            status.setRollbackOnly();
        });

        assertThat(messages.events).hasSize(1);
    }

    @Test
    @DisplayName("커밋 후 전송 실패는 저장 결과를 유지하고 메시지 내용 없이 실패를 기록한다")
    void recordsDeliveryFailureAfterCommit() {
        messages.failNextDelivery = true;

        try (var logs = new LogCapture(ChatSocketEventPublisher.class)) {
            assertThatCode(() -> transaction().executeWithoutResult(status -> {
                saveState();
                publishSuccess(ChatEventType.MESSAGE_CREATED);
            })).doesNotThrowAnyException();

            assertThat(storedRows()).isEqualTo(1);
            assertThat(messages.events).isEmpty();
            assertThat(logs.events()).hasSize(1);
            assertThat(logs.events().getFirst().getFormattedMessage())
                    .contains("10", "MESSAGE_CREATED", "message-123", "MessageDeliveryException")
                    .doesNotContain("private-message", "private-broker-secret");
            assertThat(logs.events().getFirst().getThrowableProxy()).isNull();
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void saveState() {
        jdbcTemplate.update("insert into chat_event_test (id) values (53)");
    }

    private int storedRows() {
        return jdbcTemplate.queryForObject("select count(*) from chat_event_test", Integer.class);
    }

    private void publishSuccess(ChatEventType type) {
        if (type == ChatEventType.MESSAGE_CREATED) {
            publisher.sendMessageCreated(10L, new ChatMessageCreatedPayload(
                    53L, "message-123", "민트 메리노", "private-message", null, null, EVENT_TIME));
        } else {
            publisher.sendReadUpdated(10L, new ChatReadUpdatedPayload(21L, 53L, EVENT_TIME));
        }
    }

    static class CapturedMessages {

        private final List<ChatSocketEvent<?>> events = new ArrayList<>();
        private final List<String> destinations = new ArrayList<>();
        private final List<Integer> visibleRows = new ArrayList<>();
        private boolean failNextDelivery;

        void clear() {
            events.clear();
            destinations.clear();
            visibleRows.clear();
            failNextDelivery = false;
        }

        int countCommittedRows(DataSource dataSource) {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select count(*) from chat_event_test")) {
                rows.next();
                return rows.getInt(1);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ChatSocketEventPublisher.class)
    static class TestConfig {

        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        CapturedMessages capturedMessages() {
            return new CapturedMessages();
        }

        @Bean
        SimpMessagingTemplate messagingTemplate(CapturedMessages messages, DataSource dataSource) {
            ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
            channel.subscribe(message -> {
                if (messages.failNextDelivery) {
                    messages.failNextDelivery = false;
                    throw new MessageDeliveryException("private-broker-secret");
                }
                messages.events.add((ChatSocketEvent<?>) message.getPayload());
                messages.destinations.add((String) message.getHeaders().get("simpDestination"));
                messages.visibleRows.add(messages.countCommittedRows(dataSource));
            });
            return new SimpMessagingTemplate(channel);
        }
    }
}
