package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRecommendationRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.UserInterestFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 도안 서비스 테스트")
class PatternRecommendationServiceTest {

    @Mock
    private PatternRecommendationRepository recommendationRepository;

    @Mock
    private RandomGenerator patternRecommendationRandom;

    @Mock
    private UserInterestRepository userInterestRepository;

    @Mock
    private PatternListItemResponseFactory responseFactory;

    @InjectMocks
    private PatternRecommendationService recommendationService;

    @Test
    @DisplayName("비로그인 사용자는 기본 추천 목록을 조회해야 한다")
    void getRecommendedPatterns_AnonymousUser_ReturnsDefaultRecommend() {
        Pattern pattern = pattern(10L);
        PatternListItemResponse item = item(pattern);
        when(recommendationRepository.findPopular(30)).thenReturn(List.of(pattern));
        when(responseFactory.createAll(List.of(pattern), null)).thenReturn(List.of(item));

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(null);

        assertThat(response.items()).containsExactly(item);
        verifyNoInteractions(userInterestRepository);
    }

    @Test
    @DisplayName("관심사가 없으면 기본 추천 목록을 조회해야 한다")
    void getRecommendedPatterns_NoInterests_ReturnsDefaultRecommend() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(11L);
        when(userInterestRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(recommendationRepository.findPopular(30)).thenReturn(List.of(pattern));
        when(responseFactory.createAll(List.of(pattern), user)).thenReturn(List.of(item(pattern)));

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(1);
        verify(recommendationRepository).findPopular(30);
    }

    @Test
    @DisplayName("일반 관심사는 관심사 번호 조회를 사용해야 한다")
    void getRecommendedPatterns_GeneralInterest_UsesInterestQuery() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(12L);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "빈티지")));
        when(recommendationRepository.countMatching(List.of(1), List.of())).thenReturn(1L);
        when(recommendationRepository.findMatching(List.of(1), List.of(), 0, 1)).thenReturn(List.of(pattern));
        when(responseFactory.createAll(List.of(pattern), user)).thenReturn(List.of(item(pattern)));

        recommendationService.getRecommendedPatterns(user);

        verify(recommendationRepository).findMatching(List.of(1), List.of(), 0, 1);
    }

    @Test
    @DisplayName("핏 관심사는 핏 조건이 포함된 조회를 사용해야 한다")
    void getRecommendedPatterns_FitInterest_UsesFitQuery() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(13L);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(
                        UserInterestFixture.createUserInterest(user, "빈티지"),
                        UserInterestFixture.createUserInterest(user, "슬림핏")
                ));
        when(recommendationRepository.countMatching(List.of(1, 6), List.of(6))).thenReturn(1L);
        when(recommendationRepository.findMatching(List.of(1, 6), List.of(6), 0, 1)).thenReturn(List.of(pattern));
        when(responseFactory.createAll(List.of(pattern), user)).thenReturn(List.of(item(pattern)));

        recommendationService.getRecommendedPatterns(user);

        verify(recommendationRepository).findMatching(List.of(1, 6), List.of(6), 0, 1);
    }

    @Test
    @DisplayName("관심사 매칭 결과가 없으면 기본 추천 목록을 반환해야 한다")
    void getRecommendedPatterns_NoMatch_FallsBackToDefault() {
        User user = UserFixture.createUserWithId(1L);
        Pattern fallback = pattern(14L);
        PatternListItemResponse fallbackItem = item(fallback);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "빈티지")));
        when(recommendationRepository.findPopular(30)).thenReturn(List.of(fallback));
        when(responseFactory.createAll(List.of(fallback), user)).thenReturn(List.of(fallbackItem));

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).containsExactly(fallbackItem);
        verify(recommendationRepository).findPopular(30);
    }

    @Test
    @DisplayName("핏 관심사 매칭 결과가 없으면 기본 추천 목록을 반환해야 한다")
    void getRecommendedPatterns_FitNoMatch_FallsBackToDefault() {
        User user = UserFixture.createUserWithId(1L);
        Pattern fallback = pattern(15L);
        PatternListItemResponse fallbackItem = item(fallback);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "오버사이즈")));
        when(recommendationRepository.findPopular(30)).thenReturn(List.of(fallback));
        when(responseFactory.createAll(List.of(fallback), user)).thenReturn(List.of(fallbackItem));

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).containsExactly(fallbackItem);
        verify(recommendationRepository).countMatching(List.of(5), List.of(5));
    }

    @Test
    @DisplayName("후보 변경으로 앞뒤 조회 구간이 겹쳐도 같은 도안은 한 번만 응답해야 한다")
    void getRecommendedPatterns_OverlappingWindows_ReturnsUniquePatterns() {
        User user = UserFixture.createUserWithId(1L);
        List<Pattern> head = new ArrayList<>(LongStream.rangeClosed(1, 27).mapToObj(this::pattern).toList());
        head.add(pattern(29L));
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "빈티지")));
        when(recommendationRepository.countMatching(List.of(1), List.of())).thenReturn(31L);
        when(patternRecommendationRandom.nextInt(31)).thenReturn(29);
        when(recommendationRepository.findMatching(List.of(1), List.of(), 29, 30))
                .thenReturn(List.of(pattern(29L), pattern(30L)));
        when(recommendationRepository.findMatching(List.of(1), List.of(), 0, 28)).thenReturn(head);
        var factory = new PatternListItemResponseFactory(mock(ScrapRepository.class), mock(ImageService.class));
        var service = new PatternRecommendationService(
                recommendationRepository, userInterestRepository, factory, patternRecommendationRandom);

        PatternItemsResponse response = service.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(29);
        assertThat(response.items()).extracting(PatternListItemResponse::id)
                .containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(1, 30).filter(id -> id != 28).boxed().toList());
    }

    private Pattern pattern(Long id) {
        return PatternFixture.createPatternWithId(id);
    }

    private PatternListItemResponse item(Pattern pattern) {
        return PatternListItemResponse.from(pattern, false, pattern.getThumbnailUrl());
    }
}
