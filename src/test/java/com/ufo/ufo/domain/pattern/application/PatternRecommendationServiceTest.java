package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.UserInterestFixture;
import java.util.List;
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
    private PatternRepository patternRepository;

    @Mock
    private UserInterestRepository userInterestRepository;

    @Mock
    private PatternListItemResponseFactory responseFactory;

    @InjectMocks
    private PatternRecommendationService recommendationService;

    @Test
    @DisplayName("비로그인 사용자는 기본 추천 목록을 조회한다")
    void getRecommendedPatterns_AnonymousUser_ReturnsDefaultRecommend() {
        Pattern pattern = pattern(10L);
        PatternListItemResponse item = item(pattern);
        when(patternRepository.findRecommended()).thenReturn(List.of(pattern));
        when(responseFactory.create(pattern, null)).thenReturn(item);

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(null);

        assertThat(response.items()).containsExactly(item);
        verifyNoInteractions(userInterestRepository);
    }

    @Test
    @DisplayName("관심사가 없으면 기본 추천 목록을 조회한다")
    void getRecommendedPatterns_NoInterests_ReturnsDefaultRecommend() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(11L);
        when(userInterestRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(patternRepository.findRecommended()).thenReturn(List.of(pattern));
        when(responseFactory.create(pattern, user)).thenReturn(item(pattern));

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(1);
        verify(patternRepository).findRecommended();
    }

    @Test
    @DisplayName("일반 관심사는 관심사 번호 조회를 사용한다")
    void getRecommendedPatterns_GeneralInterest_UsesInterestQuery() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(12L);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "빈티지")));
        when(patternRepository.findRecommendedByInterestNumbers(List.of(1))).thenReturn(List.of(pattern));
        when(responseFactory.create(pattern, user)).thenReturn(item(pattern));

        recommendationService.getRecommendedPatterns(user);

        verify(patternRepository).findRecommendedByInterestNumbers(List.of(1));
    }

    @Test
    @DisplayName("핏 관심사는 핏 조건이 포함된 조회를 사용한다")
    void getRecommendedPatterns_FitInterest_UsesFitQuery() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = pattern(13L);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(
                        UserInterestFixture.createUserInterest(user, "빈티지"),
                        UserInterestFixture.createUserInterest(user, "슬림핏")
                ));
        when(patternRepository.findRecommendedByInterestNumbersWithFit(List.of(1, 6), List.of(6)))
                .thenReturn(List.of(pattern));
        when(responseFactory.create(pattern, user)).thenReturn(item(pattern));

        recommendationService.getRecommendedPatterns(user);

        verify(patternRepository).findRecommendedByInterestNumbersWithFit(List.of(1, 6), List.of(6));
    }

    @Test
    @DisplayName("관심사 매칭 결과가 없으면 기본 추천 목록을 반환한다")
    void getRecommendedPatterns_NoMatch_FallsBackToDefault() {
        User user = UserFixture.createUserWithId(1L);
        Pattern fallback = pattern(14L);
        PatternListItemResponse fallbackItem = item(fallback);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "빈티지")));
        when(patternRepository.findRecommendedByInterestNumbers(List.of(1))).thenReturn(List.of());
        when(patternRepository.findRecommended()).thenReturn(List.of(fallback));
        when(responseFactory.create(fallback, user)).thenReturn(fallbackItem);

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).containsExactly(fallbackItem);
        verify(patternRepository).findRecommended();
    }

    @Test
    @DisplayName("핏 관심사 매칭 결과가 없으면 기본 추천 목록을 반환한다")
    void getRecommendedPatterns_FitNoMatch_FallsBackToDefault() {
        User user = UserFixture.createUserWithId(1L);
        Pattern fallback = pattern(15L);
        PatternListItemResponse fallbackItem = item(fallback);
        when(userInterestRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(UserInterestFixture.createUserInterest(user, "오버사이즈")));
        when(patternRepository.findRecommendedByInterestNumbersWithFit(List.of(5), List.of(5)))
                .thenReturn(List.of());
        when(patternRepository.findRecommended()).thenReturn(List.of(fallback));
        when(responseFactory.create(fallback, user)).thenReturn(fallbackItem);

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(user);

        assertThat(response.items()).containsExactly(fallbackItem);
        verify(patternRepository).findRecommendedByInterestNumbersWithFit(List.of(5), List.of(5));
    }

    private Pattern pattern(Long id) {
        return PatternFixture.createPatternWithId(id);
    }

    private PatternListItemResponse item(Pattern pattern) {
        return PatternListItemResponse.from(pattern, false, pattern.getThumbnailUrl());
    }
}
