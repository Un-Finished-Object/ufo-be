package com.ufo.ufo.support.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DisplayName("테스트 DB 선택 설정")
class TestDatabaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDatabaseConfig.class);

    @Test
    @DisplayName("DB를 지정하지 않으면 MySQL만 선언한다")
    void defaultsToMysql() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MySQLContainer.class);
            assertThat(context).doesNotHaveBean(PostgreSQLContainer.class);
        });
    }

    @Test
    @DisplayName("MySQL을 선택하면 MySQL만 선언한다")
    void selectsMysql() {
        contextRunner.withPropertyValues("ufo.test.database=mysql").run(context -> {
            assertThat(context).hasSingleBean(MySQLContainer.class);
            assertThat(context).doesNotHaveBean(PostgreSQLContainer.class);
        });
    }

    @Test
    @DisplayName("PostgreSQL을 선택하면 PostgreSQL만 선언한다")
    void selectsPostgres() {
        contextRunner.withPropertyValues("ufo.test.database=postgres").run(context -> {
            assertThat(context).hasSingleBean(PostgreSQLContainer.class);
            assertThat(context).doesNotHaveBean(MySQLContainer.class);
        });
    }

    @Test
    @DisplayName("지원하지 않는 DB를 MySQL로 대체하지 않는다")
    void doesNotFallbackForUnsupportedValue() {
        assertNoDatabase("sqlite");
    }

    @Test
    @DisplayName("빈 DB 선택값을 MySQL로 대체하지 않는다")
    void doesNotFallbackForBlankValue() {
        assertNoDatabase("");
    }

    private void assertNoDatabase(String value) {
        contextRunner.withPropertyValues("ufo.test.database=" + value).run(context -> {
            assertThat(context).doesNotHaveBean(MySQLContainer.class);
            assertThat(context).doesNotHaveBean(PostgreSQLContainer.class);
        });
    }
}
