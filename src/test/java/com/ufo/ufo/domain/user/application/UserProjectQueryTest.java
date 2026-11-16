package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {
        "spring.config.name=application-db-test",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ufo.ufo.domain.user.application.UserProjectQueryContract$ProjectQueryInspector"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, UserProjectService.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("구매 프로젝트 DB 페이지 조회 테스트")
class UserProjectQueryTest extends UserProjectQueryContract {
}
