package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.config.PatternRecommendationConfig;
import com.ufo.ufo.domain.pattern.dao.PatternRecommendationRepository;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.config.name=application-db-test",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ufo.ufo.domain.pattern.application.PatternScrapQueryContract$ScrapQueryInspector"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, PatternQueryService.class,
        PatternRecommendationService.class, PatternListItemResponseFactory.class,
        PatternScrapQueryContract.ImageConfig.class, PatternRecommendationRepository.class,
        PatternRecommendationConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 도안 목록과 검색 및 추천의 찜 SQL 조회 통합 테스트")
class PatternScrapQueryIntegrationTest extends PatternScrapQueryContract {
}
