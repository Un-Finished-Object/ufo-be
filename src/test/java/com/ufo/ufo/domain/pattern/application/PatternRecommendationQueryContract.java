package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.UserInterestFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class PatternRecommendationQueryContract {

    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserInterestRepository interestRepository;
    @Autowired
    private PatternRecommendationService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ImageService imageService;

    @MockitoBean
    private RandomGenerator patternRecommendationRandom;

    @BeforeEach
    void setUpImageUrls() {
        when(imageService.buildImageUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearQueries() {
        RecommendationQueryInspector.QUERIES.remove();
    }

    @ParameterizedTest
    @EnumSource(RecommendationMode.class)
    @DisplayName("모든 추천 분기는 최대 30개의 도안만 DB에서 읽고 응답해야 한다")
    void boundsEveryRecommendationBranch(RecommendationMode mode) {
        User user = mode == RecommendationMode.ANONYMOUS ? null : saveUser();
        if (mode == RecommendationMode.GENERAL || mode == RecommendationMode.NO_MATCH) {
            saveInterest(user, "빈티지");
        }
        if (mode == RecommendationMode.FIT || mode == RecommendationMode.FIT_NO_MATCH) {
            saveInterest(user, "빈티지");
            saveInterest(user, "슬림핏");
        }
        List<Integer> keywords = mode == RecommendationMode.NO_MATCH || mode == RecommendationMode.FIT_NO_MATCH
                ? List.of(2) : List.of(1, 6);
        savePatterns(65, keywords);
        prepareQuery();

        List<PatternListItemResponse> items = service.getRecommendedPatterns(user).items();

        assertThat(items).hasSize(30);
        assertThat(items).extracting(PatternListItemResponse::id).doesNotHaveDuplicates();
        assertBoundedPatternQueries(30);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 29, 30, 31})
    @DisplayName("관심사 추천은 후보 수가 30개보다 적으면 후보만 반환하고 상한을 지켜야 한다")
    void handlesCandidateBoundaries(int size) {
        User user = saveUser();
        saveInterest(user, "빈티지");
        List<Pattern> candidates = savePatterns(size, List.of(1));
        if (size > 0) {
            savePatterns(3, List.of(2));
        }
        prepareQuery();

        List<PatternListItemResponse> items = service.getRecommendedPatterns(user).items();

        assertThat(items).hasSize(Math.min(size, 30));
        assertThat(items).extracting(PatternListItemResponse::id)
                .isSubsetOf(candidates.stream().map(Pattern::getId).toList());
        assertBoundedPatternQueries(Math.min(size, 30));
    }

    @Test
    @DisplayName("기본 추천은 삭제된 도안을 제외하고 찜 수와 ID 내림차순을 유지해야 한다")
    void preservesPopularOrderAndExcludesDeletedPatterns() {
        List<Pattern> patterns = savePatterns(31, List.of());
        patterns.getFirst().increaseScrapsCount();
        Pattern deleted = patternRepository.save(PatternFixture.createPattern());
        deleted.increaseScrapsCount();
        deleted.increaseScrapsCount();
        PatternFixture.setDeletedAt(deleted, LocalDateTime.now());
        List<Long> expectedIds = new ArrayList<>();
        expectedIds.add(patterns.getFirst().getId());
        for (int index = 30; index >= 2; index--) {
            expectedIds.add(patterns.get(index).getId());
        }
        prepareQuery();

        List<PatternListItemResponse> items = service.getRecommendedPatterns(null).items();

        assertThat(items).extracting(PatternListItemResponse::id).containsExactlyElementsOf(expectedIds);
        assertBoundedPatternQueries(30);
    }

    @Test
    @DisplayName("핏 조건과 관심사 조건은 기존 규칙을 유지하고 중복 및 삭제 도안을 제외해야 한다")
    void preservesInterestAndFitConditions() {
        User user = saveUser();
        saveInterest(user, "빈티지");
        saveInterest(user, "슬림핏");
        Pattern vintageSlim = savePatterns(1, List.of(1, 6)).getFirst();
        Pattern slimOnly = savePatterns(1, List.of(6)).getFirst();
        savePatterns(1, List.of(1));
        savePatterns(1, List.of(1, 5));
        savePatterns(1, List.of(2));
        Pattern deleted = savePatterns(1, List.of(1, 6)).getFirst();
        PatternFixture.setDeletedAt(deleted, LocalDateTime.now());
        prepareQuery();

        List<PatternListItemResponse> items = service.getRecommendedPatterns(user).items();

        assertThat(items).extracting(PatternListItemResponse::id)
                .containsExactlyInAnyOrder(vintageSlim.getId(), slimOnly.getId());
        assertBoundedPatternQueries(2);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "60, false", "64, false", "60, true"})
    @DisplayName("개인화 후보의 뒤쪽도 선택할 수 있고 끝 구간에서는 앞부분에서 남은 도안을 조회해야 한다")
    void samplesAcrossTheEntireCandidateSet(int offset, boolean fit) {
        User user = saveUser();
        saveInterest(user, "빈티지");
        if (fit) {
            saveInterest(user, "슬림핏");
        }
        List<Pattern> patterns = savePatterns(65, List.of(1, 6));
        savePatterns(3, List.of(2));
        Pattern deleted = savePatterns(1, List.of(1, 6)).getFirst();
        PatternFixture.setDeletedAt(deleted, LocalDateTime.now());
        when(patternRecommendationRandom.nextInt(65)).thenReturn(offset);
        List<Pattern> expected = switch (offset) {
            case 0 -> patterns.subList(0, 30);
            case 60 -> Stream.concat(patterns.subList(60, 65).stream(), patterns.subList(0, 25).stream()).toList();
            case 64 -> Stream.concat(patterns.subList(64, 65).stream(), patterns.subList(0, 29).stream()).toList();
            default -> throw new IllegalArgumentException("지원하지 않는 테스트 시작 위치");
        };
        prepareQuery();

        List<PatternListItemResponse> items = service.getRecommendedPatterns(user).items();

        assertThat(items).extracting(PatternListItemResponse::id)
                .containsExactlyInAnyOrderElementsOf(expected.stream().map(Pattern::getId).toList());
        assertBoundedPatternQueries(30);
        assertThat(RecommendationQueryInspector.QUERIES.get()).hasSize(offset == 0 ? 1 : 2);
    }

    @Test
    @DisplayName("모든 시작 위치를 한 번씩 선택하면 각 관심사 후보의 노출 횟수가 같아야 한다")
    void givesEveryCandidateEqualExposureAcrossAllStartingOffsets() {
        User user = saveUser();
        saveInterest(user, "빈티지");
        List<Pattern> candidates = savePatterns(31, List.of(1));
        Map<Long, Integer> exposures = new HashMap<>();
        candidates.forEach(pattern -> exposures.put(pattern.getId(), 0));

        for (int offset = 0; offset < 31; offset++) {
            when(patternRecommendationRandom.nextInt(31)).thenReturn(offset);
            prepareQuery();

            List<PatternListItemResponse> items = service.getRecommendedPatterns(user).items();

            assertThat(items).hasSize(30);
            assertThat(items).extracting(PatternListItemResponse::id).doesNotHaveDuplicates();
            assertBoundedPatternQueries(30);
            items.forEach(item -> exposures.merge(item.id(), 1, Integer::sum));
        }

        assertThat(exposures).hasSize(31);
        assertThat(exposures.values()).containsOnly(30);
    }

    private User saveUser() {
        return userRepository.save(UserFixture.createUser("recommend@example.com", Role.ROLE_USER));
    }

    private void saveInterest(User user, String keyword) {
        interestRepository.save(UserInterestFixture.createUserInterest(user, keyword));
    }

    private List<Pattern> savePatterns(int size, List<Integer> keywords) {
        List<Pattern> patterns = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            patterns.add(patternRepository.save(PatternFixture.createPatternWithInterestNumbers(
                    "pattern" + index, "designer", "apparel", "long_sweater", "patterns/test.png", keywords)));
        }
        return patterns;
    }

    private void prepareQuery() {
        entityManager.flush();
        entityManager.clear();
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics().clear();
        RecommendationQueryInspector.QUERIES.get().clear();
    }

    private void assertBoundedPatternQueries(int expectedLoads) {
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.getEntityStatistics(Pattern.class.getName()).getLoadCount()).isEqualTo(expectedLoads);
        assertThat(RecommendationQueryInspector.QUERIES.get()).allSatisfy(sql ->
                assertThat(sql).containsAnyOf(" limit ", " fetch first ", " fetch next "));
        assertThat(RecommendationQueryInspector.QUERIES.get()).hasSizeLessThanOrEqualTo(2);
    }

    enum RecommendationMode {
        ANONYMOUS, NO_INTERESTS, GENERAL, FIT, NO_MATCH, FIT_NO_MATCH
    }

    public static class RecommendationQueryInspector implements StatementInspector {

        private static final ThreadLocal<List<String>> QUERIES = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.matches(".*\\bfrom patterns\\b.*") && !normalized.startsWith("select count(")) {
                QUERIES.get().add(normalized);
            }
            return sql;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ImageConfig {

        @Bean
        ImageService imageService() {
            return mock(ImageService.class);
        }
    }
}
