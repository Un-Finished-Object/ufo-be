package com.ufo.ufo.domain.pattern.application;

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
                + "com.ufo.ufo.domain.pattern.application.AlternativeYarnQueryContract$AlternativeQueryInspector"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, AlternativeYarnQueryService.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대체 실 후보 DB 조회 제한 테스트")
class AlternativeYarnQueryTest extends AlternativeYarnQueryContract {
}
