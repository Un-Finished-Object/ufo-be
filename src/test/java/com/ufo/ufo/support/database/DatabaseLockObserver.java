package com.ufo.ufo.support.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

public class DatabaseLockObserver {

    private static final String MYSQL_LOCK_WAIT_QUERY = """
            SELECT COUNT(*)
            FROM performance_schema.data_lock_waits waits
            JOIN performance_schema.threads waiting ON waiting.THREAD_ID = waits.REQUESTING_THREAD_ID
            JOIN performance_schema.threads blocking ON blocking.THREAD_ID = waits.BLOCKING_THREAD_ID
            WHERE waiting.PROCESSLIST_ID = ? AND blocking.PROCESSLIST_ID = ?
            """;
    private static final String POSTGRES_LOCK_WAIT_QUERY = """
            SELECT COUNT(*) FROM pg_stat_activity
            WHERE pid = ? AND ? = ANY(pg_blocking_pids(pid))
            """;

    private final DataSource dataSource;
    private final MySQLContainer mysqlContainer;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseLockObserver(DataSource dataSource, MySQLContainer mysqlContainer) {
        this.dataSource = dataSource;
        this.mysqlContainer = mysqlContainer;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public long currentSessionId() {
        String query = mysqlContainer == null ? "SELECT pg_backend_pid()" : "SELECT CONNECTION_ID()";
        return jdbcTemplate.queryForObject(query, Long.class);
    }

    public void awaitBlockedBy(long waitingSessionId, long blockingSessionId, Future<?> request) {
        String query = mysqlContainer == null ? POSTGRES_LOCK_WAIT_QUERY : MYSQL_LOCK_WAIT_QUERY;
        try (Connection connection = observerConnection();
                var statement = connection.prepareStatement(query)) {
            statement.setLong(1, waitingSessionId);
            statement.setLong(2, blockingSessionId);
            statement.setQueryTimeout(2);
            await().alias("DB 세션 %d가 세션 %d의 락을 기다린다".formatted(waitingSessionId, blockingSessionId))
                    .pollInSameThread()
                    .pollDelay(Duration.ZERO)
                    .pollInterval(Duration.ofMillis(20))
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        if (request.isDone()) {
                            request.get();
                            throw new AssertionError("락 대기를 확인하기 전에 요청이 완료되었습니다.");
                        }
                        try (var result = statement.executeQuery()) {
                            assertThat(result.next()).isTrue();
                            assertThat(result.getLong(1)).isPositive();
                        }
                    });
        } catch (SQLException exception) {
            throw new IllegalStateException("테스트 DB의 락 대기 정보를 조회할 수 없습니다.", exception);
        }
    }

    private Connection observerConnection() throws SQLException {
        if (mysqlContainer != null) {
            // Testcontainers DB에서만 root로 접속해 다른 세션의 Performance Schema 정보를 읽는다.
            return DriverManager.getConnection(mysqlContainer.getJdbcUrl(), "root", mysqlContainer.getPassword());
        }
        // 진행 중인 JPA 트랜잭션과 별개인 연결로 락 대기를 관찰한다.
        return dataSource.getConnection();
    }
}
