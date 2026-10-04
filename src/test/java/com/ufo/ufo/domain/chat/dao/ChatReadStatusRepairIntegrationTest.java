package com.ufo.ufo.domain.chat.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest(properties = "spring.config.name=application-db-test")
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import(TestDatabaseConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("기존 채팅 읽음 데이터 정리 SQL 통합 테스트")
class ChatReadStatusRepairIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createLegacyTables() {
        jdbc.execute("CREATE TABLE chat_messages (chat_message_id BIGINT PRIMARY KEY, chat_room_id BIGINT NOT NULL)");
        jdbc.execute("""
                CREATE TABLE chat_read_statuses (
                    chat_read_status_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, chat_room_id BIGINT NOT NULL,
                    last_read_message_id BIGINT, read_at TIMESTAMP NULL
                )
                """);
        jdbc.update("INSERT INTO chat_messages VALUES (10, 1), (20, 1), (30, 2)");
        jdbc.update("""
                INSERT INTO chat_read_statuses VALUES
                    (1, 1, 1, NULL, NULL),
                    (2, 1, 1, 10, '2026-10-04 10:00:00'),
                    (3, 1, 1, 20, '2026-10-04 11:00:00'),
                    (4, 1, 1, 20, '2026-10-04 12:00:00'),
                    (5, 1, 1, 999, '2026-10-04 13:00:00'),
                    (6, 1, 1, 30, '2026-10-04 14:00:00'),
                    (7, 2, 1, 30, '2026-10-04 14:00:00'),
                    (8, 3, 2, 30, '2026-10-04 15:00:00')
                """);
    }

    @AfterEach
    void dropLegacyTables() {
        jdbc.execute("DROP TABLE chat_read_statuses");
        jdbc.execute("DROP TABLE chat_messages");
    }

    @Test
    @DisplayName("중복 행은 최신 유효 메시지와 그 읽음 시각을 보존한 뒤 유일 제약을 적용한다")
    void repairPreservesLatestValidPositionAndAddsConstraint() throws SQLException {
        repair(true);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_read_statuses", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT last_read_message_id FROM chat_read_statuses WHERE chat_read_status_id=1", Long.class))
                .isEqualTo(20L);
        assertThat(jdbc.queryForObject("SELECT read_at FROM chat_read_statuses WHERE chat_read_status_id=1",
                java.sql.Timestamp.class).toLocalDateTime()).isEqualTo("2026-10-04T12:00:00");
        assertThat(jdbc.queryForObject(
                "SELECT last_read_message_id FROM chat_read_statuses WHERE chat_read_status_id=7", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT read_at FROM chat_read_statuses WHERE chat_read_status_id=7",
                java.sql.Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT last_read_message_id FROM chat_read_statuses WHERE chat_read_status_id=8", Long.class))
                .isEqualTo(30L);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("scripts/database/chat-read-status-constraint.sql"));
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO chat_read_statuses VALUES (9, 1, 1, 20, NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정리 결과를 커밋하지 않으면 원래의 읽음 행을 복구할 수 있다")
    void repairCanBeRolledBackBeforeConstraint() throws SQLException {
        repair(false);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_read_statuses", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT last_read_message_id FROM chat_read_statuses WHERE chat_read_status_id=5", Long.class))
                .isEqualTo(999L);
    }

    private void repair(boolean commit) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(connection,
                        new FileSystemResource("scripts/database/chat-read-status-repair.sql"));
                if (commit) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
            } finally {
                connection.rollback();
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS ufo_read_status_repair");
                    statement.execute("DROP TABLE IF EXISTS ufo_read_status_groups");
                }
                connection.commit();
            }
        }
    }
}
