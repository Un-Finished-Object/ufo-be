package com.ufo.ufo.support.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.JdbcDatabaseContainer;

@JdbcTest(properties = "spring.config.name=application-db-test")
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import(TestDatabaseConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB JDBC 통합 테스트")
class JdbcIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JdbcConnectionDetails connectionDetails;
    @Autowired
    private JdbcDatabaseContainer<?> container;
    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void createTable() {
        jdbcTemplate.execute("CREATE TABLE jdbc_test_records (id INTEGER PRIMARY KEY, label VARCHAR(50))");
    }

    @AfterEach
    void dropTable() {
        jdbcTemplate.execute("DROP TABLE jdbc_test_records");
    }

    @Test
    @DisplayName("선택한 컨테이너의 실제 DB에 연결하며 JPA 설정을 불러오지 않는다")
    void connectsToSelectedContainer() throws SQLException {
        String selectedDatabase = System.getProperty("ufo.test.database", "mysql");
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo(selectedDatabase.equals("mysql") ? "MySQL" : "PostgreSQL");
            // Drivers can normalize URL parameters, so compare Boot's supplied connection details directly.
            assertThat(connectionDetails.getJdbcUrl()).isEqualTo(container.getJdbcUrl());
            assertThat(connection.getMetaData().getURL()).startsWith(
                    selectedDatabase.equals("mysql") ? "jdbc:mysql://" : "jdbc:postgresql://");
        }
        assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
    }

    @Test
    @DisplayName("PreparedStatement로 생성·조회·수정·삭제한다")
    void preparedStatementsSupportCrud() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(write(connection, "INSERT INTO jdbc_test_records VALUES (?, ?)", 1, "before"))
                    .isEqualTo(1);
            assertThat(readLabel(connection, 1)).isEqualTo("before");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE jdbc_test_records SET label = ? WHERE id = ?")) {
                statement.setString(1, "after");
                statement.setInt(2, 1);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            assertThat(readLabel(connection, 1)).isEqualTo("after");
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM jdbc_test_records WHERE id = ?")) {
                statement.setInt(1, 1);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            assertThat(readLabel(connection, 1)).isNull();
        }
    }

    @Test
    @DisplayName("명시적으로 롤백한 데이터는 다른 연결에서도 남지 않는다")
    void rollbackDoesNotPersistChanges() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            write(connection, "INSERT INTO jdbc_test_records VALUES (?, ?)", 2, "rolled-back");
            assertThat(readLabel(connection, 2)).isEqualTo("rolled-back");
            connection.rollback();
        }
        try (Connection connection = dataSource.getConnection()) {
            assertThat(readLabel(connection, 2)).isNull();
        }
    }

    private int write(Connection connection, String sql, int id, String label) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, label);
            return statement.executeUpdate();
        }
    }

    private String readLabel(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT label FROM jdbc_test_records WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }
}
