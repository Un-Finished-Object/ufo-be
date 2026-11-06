package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.fixture.ChatOutboxPoolTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.config.name=application-db-test", "spring.datasource.generate-unique-name=true",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=500"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, ChatSocketEventPublisher.class, ChatOutboxPoolTestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("채팅 이벤트 단일 DB 연결 전송 테스트")
class ChatOutboxConnectionTest extends ChatOutboxConnectionContract {
}
