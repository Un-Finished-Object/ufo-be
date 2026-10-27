package com.ufo.ufo.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.JdbcDatabaseContainer;

@JdbcTest(properties = {"spring.config.name=application-db-test", "spring.flyway.enabled=false"})
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import(TestDatabaseConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@EnabledIfSystemProperty(named = "ufo.test.database", matches = "mysql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("MySQL 스키마 마이그레이션")
class DatabaseMigrationIntegrationTest {

    private static final String LOCATION = "classpath:db/migration/mysql";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JdbcDatabaseContainer<?> container;
    @TempDir
    private Path directory;

    @BeforeEach
    @AfterEach
    void clearTestSchema() {
        flyway().clean();
    }

    @Test
    @DisplayName("빈 DB에 스키마를 만들고 다시 실행해도 SQL을 중복 적용하지 않는다")
    void createsSchemaOnce() {
        Flyway migrations = flyway();

        assertThat(migrations.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(migrations.migrate().migrationsExecuted).isZero();
        assertThat(tableCount()).isEqualTo(22);
        assertThat(readStatusUniqueCount()).isEqualTo(1);
        migrations.validate();
    }

    @Test
    @DisplayName("이력이 없는 기존 DB는 자동으로 baseline을 등록하지 않는다")
    void refusesUnverifiedExistingDatabase() {
        prepareLegacySchema();

        assertThatThrownBy(() -> flyway().migrate()).isInstanceOf(FlywayException.class);
        assertThat(tableCount()).isEqualTo(21);
    }

    @Test
    @DisplayName("기존 데이터를 유지한 채 baseline과 후속 제약을 적용한다")
    void adoptsExistingSchemaWithoutChangingData() {
        prepareLegacySchema();
        insertUser();

        registerBaseline();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ball_balance FROM users WHERE user_id=1", Integer.class))
                .isEqualTo(150);
        assertThat(readStatusUniqueCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='patterns' AND COLUMN_NAME='required_yarn_amount'
                """, Integer.class)).isEqualTo(1700);
    }

    @Test
    @DisplayName("Gradle 표준 baseline 작업은 기존 데이터를 유지하고 후속 SQL을 실행하지 않는다")
    void registersBaselineWithGradleTask() throws Exception {
        prepareLegacySchema();
        insertUser();
        Path output = directory.resolve("flyway-baseline.log");
        List<String> command = System.getProperty("os.name").startsWith("Windows")
                ? List.of("cmd.exe", "/c", "gradlew.bat", "flywayBaseline", "--no-daemon", "--console=plain")
                : List.of("sh", "gradlew", "flywayBaseline", "--no-daemon", "--console=plain");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("FLYWAY_"));
        builder.environment().put("DB_URL", container.getJdbcUrl());
        builder.environment().put("DB_USERNAME", container.getUsername());
        builder.environment().put("DB_PASSWORD", container.getPassword());
        Process process = builder.start();
        try {
            assertThat(process.waitFor(2, TimeUnit.MINUTES)).as("baseline 작업 완료").isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(output)).isZero();
        } finally {
            process.destroyForcibly();
        }

        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE type='BASELINE'", String.class))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT ball_balance FROM users WHERE user_id=1", Integer.class))
                .isEqualTo(150);
        assertThat(readStatusUniqueCount()).isZero();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 이름과 열 순서로 수동 추가한 UNIQUE 제약은 다시 만들지 않는다")
    void keepsPreviouslyAddedConstraint() {
        prepareLegacySchema();
        jdbc.execute("ALTER TABLE chat_read_statuses ADD CONSTRAINT manual_read_key UNIQUE(chat_room_id, user_id)");

        registerBaseline();
        flyway().migrate();

        assertThat(readStatusUniqueCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_read_statuses' AND INDEX_NAME='manual_read_key'
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("적용한 SQL이 수정되면 검증에 실패하고 DB는 바꾸지 않는다")
    void detectsChangedMigration() throws Exception {
        flyway().migrate();
        Path source = Path.of("src/main/resources/db/migration/mysql");
        Files.writeString(directory.resolve("V1__initial_schema.sql"),
                Files.readString(source.resolve("V1__initial_schema.sql"))
                        + "\nALTER TABLE users ADD COLUMN unexpected_column INT;\n");
        Files.copy(source.resolve("V2__ensure_chat_read_status_unique.sql"),
                directory.resolve("V2__ensure_chat_read_status_unique.sql"));
        Flyway changed = Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + directory.toAbsolutePath()).load();

        assertThatThrownBy(changed::validate).isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='unexpected_column'
                """, Integer.class)).isZero();
    }

    @Test
    @DisplayName("중복 데이터로 제약 적용이 실패하면 데이터를 유지하고 정리 후 다시 적용한다")
    void preservesDataAfterFailedMigration() {
        prepareLegacySchema();
        insertUser();
        jdbc.execute("""
                INSERT INTO patterns(pattern_id, title, designer, scraps_count, view_count)
                VALUES(1, 'test', 'test', 0, 0)
                """);
        jdbc.execute("""
                INSERT INTO chat_rooms(chat_room_id, pattern_id, segment_start_at, segment_end_at)
                VALUES(1, 1, '2026-10-01', '2026-10-08')
                """);
        jdbc.execute("""
                INSERT INTO chat_read_statuses(chat_read_status_id, user_id, chat_room_id)
                VALUES(1, 1, 1), (2, 1, 1)
                """);
        registerBaseline();

        assertThatThrownBy(() -> flyway().migrate()).isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_read_statuses", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT ball_balance FROM users WHERE user_id=1", Integer.class))
                .isEqualTo(150);

        jdbc.execute("DELETE FROM chat_read_statuses WHERE chat_read_status_id=2");
        flyway().repair();
        flyway().migrate();

        assertThat(readStatusUniqueCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_read_statuses", Integer.class)).isEqualTo(1);
        flyway().validate();
    }

    @Test
    @DisplayName("스키마 조회 SQL은 읽기 전용 연결에서 실행된다")
    void inspectsSchemaWithoutWrites() throws Exception {
        flyway().migrate();
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("scripts/database/inspect-schema.sql"));
            connection.rollback();
        }
        assertThat(tableCount()).isEqualTo(22);
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(dataSource).locations(LOCATION)
                .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(false)
                .validateMigrationNaming(true).load();
    }

    private void prepareLegacySchema() {
        Flyway.configure().dataSource(dataSource).locations(LOCATION).target("1").load().migrate();
        jdbc.execute("DROP TABLE flyway_schema_history");
    }

    private void registerBaseline() {
        flyway().baseline();
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE type='BASELINE'", String.class))
                .isEqualTo("1");
    }

    private void insertUser() {
        jdbc.execute("""
                INSERT INTO users(user_id, email, nickname, ball_balance)
                VALUES(1, 'fixture@example.test', 'fixture', 150)
                """);
    }

    private int tableCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'
                """, Integer.class);
    }

    private int readStatusUniqueCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT INDEX_NAME FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_read_statuses' AND NON_UNIQUE=0
                  GROUP BY INDEX_NAME
                  HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME)='chat_room_id,user_id' AND COUNT(*)=2
                ) indexes_for_read_status
                """, Integer.class);
    }
}
