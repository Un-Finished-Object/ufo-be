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
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ufo.ufo.domain.pattern.application.PatternScrapQueryContract$ScrapQueryInspector"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, PatternQueryService.class, PatternRecommendationService.class,
        PatternListItemResponseFactory.class, PatternScrapQueryContract.ImageConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("도안 목록과 검색 및 추천의 찜 SQL 조회 테스트")
class PatternScrapQueryTest extends PatternScrapQueryContract {
}
