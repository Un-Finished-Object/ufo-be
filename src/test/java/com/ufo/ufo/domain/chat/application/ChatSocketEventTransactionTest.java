package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.fixture.ChatOutboxTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, ChatSocketEventPublisher.class, ChatOutboxTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("채팅 성공 이벤트 트랜잭션과 복구 테스트")
class ChatSocketEventTransactionTest extends ChatEventOutboxContract {
}
