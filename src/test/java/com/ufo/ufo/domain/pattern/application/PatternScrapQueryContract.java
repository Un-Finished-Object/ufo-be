package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.scrap.domain.Scrap;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class PatternScrapQueryContract {

    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private ScrapRepository scrapRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatternQueryService queryService;
    @Autowired
    private PatternRecommendationService recommendationService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("추천 도안이 30개를 넘으면 DB에서 필요한 후보만 조회해 최대 30개를 반환해야 한다")
    void recommendationResponseIsBounded() {
        savePatterns(31);
        prepareQuery();

        assertThat(recommendationService.getRecommendedPatterns(null).items()).hasSize(30);
    }

    @BeforeEach
    void setUpImageUrls() {
        when(imageService.buildImageUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearQueryCount() {
        ScrapQueryInspector.COUNT.remove();
    }

    @ParameterizedTest(name = "{0}, 도안 {1}개")
    @MethodSource("querySizes")
    @DisplayName("도안 수와 관계없이 찜 SQL을 한 번 실행하고 현재 사용자의 찜만 반영해야 한다")
    void loggedInQueryBatchesScrapStatus(QueryType type, int size) {
        User user = saveUser("reader");
        User other = saveUser("other");
        List<Pattern> patterns = savePatterns(size);
        Long scrappedId = patterns.getFirst().getId();
        scrapRepository.save(Scrap.builder().user(user).pattern(patterns.getFirst()).build());
        if (size > 1) {
            scrapRepository.save(Scrap.builder().user(other).pattern(patterns.getLast()).build());
        }
        prepareQuery();

        List<PatternListItemResponse> items = query(type, user, 1);

        assertThat(items).hasSize(size);
        assertScrappedIds(items, Set.of(scrappedId));
        assertThat(ScrapQueryInspector.COUNT.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(QueryType.class)
    @DisplayName("비로그인 조회는 찜 SQL 없이 모든 도안을 미찜으로 반환해야 한다")
    void anonymousQuerySkipsScraps(QueryType type) {
        User other = saveUser("other");
        List<Pattern> patterns = savePatterns(2);
        scrapRepository.save(Scrap.builder().user(other).pattern(patterns.getFirst()).build());
        prepareQuery();

        List<PatternListItemResponse> items = query(type, null, 1);

        assertThat(items).hasSize(2);
        assertScrappedIds(items, Set.of());
        assertThat(ScrapQueryInspector.COUNT.get()).isZero();
    }

    @ParameterizedTest
    @EnumSource(QueryType.class)
    @DisplayName("빈 조회 결과에서는 로그인 사용자도 찜 SQL을 실행하지 않아야 한다")
    void emptyQuerySkipsScraps(QueryType type) {
        User user = saveUser("reader");
        prepareQuery();

        assertThat(query(type, user, 1)).isEmpty();

        assertThat(ScrapQueryInspector.COUNT.get()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = QueryType.class, names = {"LIST", "SEARCH"})
    @DisplayName("목록과 검색은 페이지 경계에서도 찜 상태와 페이지 정보를 유지해야 한다")
    void pageBoundariesPreserveScrapsAndPagination(QueryType type) {
        User user = saveUser("reader");
        List<Pattern> patterns = savePatterns(21);
        Long scrappedId = patterns.getFirst().getId();
        scrapRepository.save(Scrap.builder().user(user).pattern(patterns.getFirst()).build());
        prepareQuery();

        var first = type == QueryType.LIST
                ? queryService.getPatterns(user, "all", null, "scraps", 1)
                : queryService.searchPatterns(user, "pattern", 1);
        assertThat(first.items()).hasSize(20);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.nextPage()).isEqualTo(1);
        assertThat(ScrapQueryInspector.COUNT.get()).isEqualTo(1);
        ScrapQueryInspector.COUNT.set(0);

        var last = type == QueryType.LIST
                ? queryService.getPatterns(user, "all", null, "scraps", 2)
                : queryService.searchPatterns(user, "pattern", 2);
        assertThat(last.items()).hasSize(1);
        assertThat(last.page()).isEqualTo(2);
        assertThat(last.nextPage()).isZero();
        assertThat(last.items()).extracting(PatternListItemResponse::id)
                .doesNotContainAnyElementsOf(first.items().stream().map(PatternListItemResponse::id).toList());
        assertScrappedIds(Stream.concat(first.items().stream(), last.items().stream()).toList(), Set.of(scrappedId));
        assertThat(ScrapQueryInspector.COUNT.get()).isEqualTo(1);
        ScrapQueryInspector.COUNT.set(0);

        assertThat(query(type, user, 3)).isEmpty();
        assertThat(ScrapQueryInspector.COUNT.get()).isZero();
    }

    private List<PatternListItemResponse> query(QueryType type, User user, int page) {
        return switch (type) {
            case LIST -> queryService.getPatterns(user, "all", null, "scraps", page).items();
            case SEARCH -> queryService.searchPatterns(user, "pattern", page).items();
            case RECOMMEND -> recommendationService.getRecommendedPatterns(user).items();
        };
    }

    private User saveUser(String name) {
        User user = UserFixture.createUser(name + "@example.com", Role.ROLE_USER);
        user.updateNameAndProfileImage(name, "defaults/profile.png");
        return userRepository.save(user);
    }

    private List<Pattern> savePatterns(int size) {
        List<Pattern> patterns = new ArrayList<>();
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int index = 0; index < size; index++) {
            Pattern pattern = patternRepository.save(PatternFixture.createPattern());
            entityManager.createNativeQuery("update patterns set created_at = :createdAt where pattern_id = :id")
                    .setParameter("createdAt", firstCreatedAt.plusSeconds(index))
                    .setParameter("id", pattern.getId())
                    .executeUpdate();
            patterns.add(pattern);
        }
        return patterns;
    }

    private void prepareQuery() {
        entityManager.flush();
        entityManager.clear();
        ScrapQueryInspector.COUNT.set(0);
    }

    private void assertScrappedIds(List<PatternListItemResponse> items, Set<Long> expected) {
        assertThat(items.stream().filter(item -> item.my().scrapped()).map(PatternListItemResponse::id))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Arguments> querySizes() {
        return Stream.of(QueryType.values())
                .flatMap(type -> Stream.of(1, 20).map(size -> Arguments.of(type, size)));
    }

    enum QueryType {
        LIST, SEARCH, RECOMMEND
    }

    public static class ScrapQueryInspector implements StatementInspector {

        private static final ThreadLocal<Integer> COUNT = ThreadLocal.withInitial(() -> 0);

        @Override
        public String inspect(String sql) {
            if (sql.toLowerCase(Locale.ROOT).matches("(?s).*\\bfrom\\s+scraps\\b.*")) {
                COUNT.set(COUNT.get() + 1);
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
