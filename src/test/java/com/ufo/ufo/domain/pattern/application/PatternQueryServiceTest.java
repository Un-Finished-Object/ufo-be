package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 조회 서비스 테스트")
class PatternQueryServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternImageRepository patternImageRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private PatternListItemResponseFactory responseFactory;

    @InjectMocks
    private PatternQueryService patternQueryService;

    @Test
    @DisplayName("도안 목록 조회는 도안 아이템 목록과 페이지를 반환해야 한다")
    void getPatterns_ReturnsItemsAndPage() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternListItemResponse item = PatternListItemResponse.from(pattern, true, pattern.getThumbnailUrl());
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pattern)));
        when(responseFactory.create(pattern, user)).thenReturn(item);

        PatternListResponse response = patternQueryService.getPatterns(user, "all", null, "news", 1);

        assertThat(response.items()).containsExactly(item);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isZero();
    }
}
