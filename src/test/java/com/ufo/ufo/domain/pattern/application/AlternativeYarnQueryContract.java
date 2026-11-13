package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternOriginalYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnAlternativeRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import com.ufo.ufo.domain.pattern.dto.response.YarnAlternativesResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnAlternativeFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class AlternativeYarnQueryContract {

    @Autowired
    private YarnRepository yarnRepository;
    @Autowired
    private YarnAlternativeRepository alternativeRepository;
    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private PatternOriginalYarnRepository originalYarnRepository;
    @Autowired
    private AlternativeYarnQueryService service;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CreditService creditService;

    private final User user = UserFixture.createUserWithId(1L);

    @BeforeEach
    void allowUnlockedYarnInfo() {
        when(creditService.isUnlocked(eq(1L), anyLong(), eq(UnlockType.YARN_INFO))).thenReturn(true);
    }

    @AfterEach
    void clearQueries() {
        AlternativeQueryInspector.CANDIDATE_QUERIES.remove();
        AlternativeQueryInspector.YARN_QUERIES.remove();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 14, 15, 16, 40})
    @DisplayName("원작 실별 후보를 DB에서 최대 15개만 읽고 선택 원작 실이 없으면 빈 목록을 반환해야 한다")
    void limitsCandidateLoadsAtTheDatabase(int size) {
        Yarn original = saveYarn();
        PatternOriginalYarn originalSet = saveOriginalSet(original, null, null);
        List<YarnAlternative> candidates = saveAlternatives(original, size);
        saveAlternatives(saveYarn(), 20);
        prepareQuery();

        YarnAlternativesResponse response = service.getAlternatives(user, originalSet.getId());

        assertThat(response.originalYarnSetId()).isEqualTo(originalSet.getId());
        assertCandidateIds(response.firstYarn(), candidates);
        assertThat(response.secondYarn()).isEmpty();
        assertThat(response.subYarn()).isEmpty();
        assertBoundedQueries(Math.min(size, 15), 1);
    }

    @Test
    @DisplayName("대체 실은 순위와 ID 오름차순으로 15개를 선택하고 실 정보와 유사도 점수를 함께 반환해야 한다")
    void preservesRankingAndIdOrderWithFetchedYarnDetails() {
        Yarn original = saveYarn();
        PatternOriginalYarn originalSet = saveOriginalSet(original, null, null);
        List<YarnAlternative> candidates = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            candidates.add(alternativeRepository.save(
                    YarnAlternativeFixture.create(original, saveYarn(), index % 4 + 1)));
        }
        List<Integer> expectedIndexes = List.of(0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11);
        List<Long> expectedIds = expectedIndexes.stream().map(index -> candidates.get(index).getId()).toList();
        Long firstYarnId = candidates.getFirst().getAlternativeYarn().getYarnId();
        prepareQuery();

        YarnAlternativesResponse response = service.getAlternatives(user, originalSet.getId());

        assertThat(response.firstYarn()).extracting(YarnAlternativesResponse.Item::altId)
                .containsExactlyElementsOf(expectedIds);
        YarnAlternativesResponse.Item first = response.firstYarn().getFirst();
        assertThat(first.yarnId()).isEqualTo(firstYarnId);
        assertThat(first.yarnName()).isEqualTo("old");
        assertThat(first.store()).isEqualTo("oldV");
        assertThat(first.componentScore()).isEqualTo(100);
        assertThat(first.lengthScore()).isEqualTo(90);
        assertThat(first.gaugeScore()).isEqualTo(80);
        assertThat(first.needleScore()).isEqualTo(70);
        assertBoundedQueries(15, 1);
    }

    @ParameterizedTest
    @CsvSource({"16, 20, 18", "0, 16, 0", "1, 0, 16"})
    @DisplayName("세 원작 실의 후보를 각각 15개로 제한하고 후보가 없는 슬롯은 빈 목록으로 반환해야 한다")
    void limitsEachOriginalYarnIndependently(int mainCount, int secondCount, int subCount) {
        Yarn main = saveYarn();
        Yarn second = saveYarn();
        Yarn sub = saveYarn();
        PatternOriginalYarn originalSet = saveOriginalSet(main, second, sub);
        List<YarnAlternative> mainCandidates = saveAlternatives(main, mainCount);
        List<YarnAlternative> secondCandidates = saveAlternatives(second, secondCount);
        List<YarnAlternative> subCandidates = saveAlternatives(sub, subCount);
        prepareQuery();

        YarnAlternativesResponse response = service.getAlternatives(user, originalSet.getId());

        assertCandidateIds(response.firstYarn(), mainCandidates);
        assertCandidateIds(response.secondYarn(), secondCandidates);
        assertCandidateIds(response.subYarn(), subCandidates);
        assertBoundedQueries(Math.min(mainCount, 15) + Math.min(secondCount, 15) + Math.min(subCount, 15), 3);
    }

    private Yarn saveYarn() {
        return yarnRepository.save(YarnFixture.createYarn());
    }

    private PatternOriginalYarn saveOriginalSet(Yarn main, Yarn second, Yarn sub) {
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());
        return originalYarnRepository.save(PatternOriginalYarn.builder()
                .pattern(pattern).mainYarn(main).secondYarn(second).subYarn(sub).build());
    }

    private List<YarnAlternative> saveAlternatives(Yarn original, int count) {
        List<YarnAlternative> alternatives = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            alternatives.add(alternativeRepository.save(YarnAlternativeFixture.create(original, saveYarn())));
        }
        return alternatives;
    }

    private void assertCandidateIds(List<YarnAlternativesResponse.Item> items, List<YarnAlternative> candidates) {
        assertThat(items).extracting(YarnAlternativesResponse.Item::altId)
                .containsExactlyElementsOf(candidates.stream().limit(15).map(YarnAlternative::getId).toList());
    }

    private void prepareQuery() {
        entityManager.flush();
        entityManager.clear();
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics().clear();
        AlternativeQueryInspector.CANDIDATE_QUERIES.get().clear();
        AlternativeQueryInspector.YARN_QUERIES.get().clear();
    }

    private void assertBoundedQueries(int expectedLoads, int expectedQueries) {
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.getEntityStatistics(YarnAlternative.class.getName()).getLoadCount())
                .isEqualTo(expectedLoads);
        assertThat(AlternativeQueryInspector.CANDIDATE_QUERIES.get()).hasSize(expectedQueries)
                .allSatisfy(sql -> {
                    assertThat(sql).containsAnyOf(" limit ", " fetch first ", " fetch next ");
                    assertThat(sql).contains(" join yarns ");
                });
        assertThat(AlternativeQueryInspector.YARN_QUERIES.get()).isEmpty();
    }

    public static class AlternativeQueryInspector implements StatementInspector {

        private static final ThreadLocal<List<String>> CANDIDATE_QUERIES = ThreadLocal.withInitial(ArrayList::new);
        private static final ThreadLocal<List<String>> YARN_QUERIES = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.matches(".*\\bfrom yarn_alternatives\\b.*")) {
                CANDIDATE_QUERIES.get().add(normalized);
            } else if (normalized.matches(".*\\bfrom yarns\\b.*")) {
                YARN_QUERIES.get().add(normalized);
            }
            return sql;
        }
    }
}
