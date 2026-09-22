package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.support.fixture.PatternFixture;
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
    @DisplayName("비로그인 추천 조회는 기본 추천 결과를 반환해야 한다")
    void getRecommendedPatterns_AnonymousUser_ReturnsDefaultRecommend() {
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternListItemResponse item = PatternListItemResponse.from(pattern, false, pattern.getThumbnailUrl());
        when(patternRepository.findRecommended()).thenReturn(List.of(pattern));
        when(responseFactory.create(pattern, null)).thenReturn(item);

        PatternItemsResponse response = recommendationService.getRecommendedPatterns(null);

        assertThat(response.items()).containsExactly(item);
    }
}
