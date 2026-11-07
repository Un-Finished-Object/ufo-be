package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 목록 응답 생성 테스트")
class PatternListItemResponseFactoryTest {

    @Mock
    private ScrapRepository scrapRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private PatternListItemResponseFactory responseFactory;

    @Test
    @DisplayName("로그인 사용자의 찜 여부와 이미지 URL을 반영해야 한다")
    void createAll_LoggedInUser_ReflectsScrapAndImageUrl() {
        var user = UserFixture.createUserWithId(1L);
        var pattern = PatternFixture.createPatternWithId(10L);
        when(scrapRepository.findScrappedPatternIds(1L, List.of(10L))).thenReturn(Set.of(10L));
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn.example.com/pattern.png");

        var response = responseFactory.createAll(List.of(pattern), user).getFirst();

        assertThat(response.my().scrapped()).isTrue();
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/pattern.png");
        assertThat(response.id()).isEqualTo(10L);
        verify(scrapRepository).findScrappedPatternIds(1L, List.of(10L));
        verifyNoMoreInteractions(scrapRepository);
    }

    @Test
    @DisplayName("비로그인 사용자의 찜 여부는 false이고 찜 조회를 하지 않아야 한다")
    void createAll_AnonymousUser_ReturnsUnscrapped() {
        var pattern = PatternFixture.createPatternWithId(10L);
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn.example.com/pattern.png");

        var response = responseFactory.createAll(List.of(pattern), null).getFirst();

        assertThat(response.my().scrapped()).isFalse();
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/pattern.png");
        verifyNoInteractions(scrapRepository);
    }

    @Test
    @DisplayName("ID가 없는 사용자의 찜 여부는 false이고 찜 조회를 하지 않아야 한다")
    void createAll_UserWithoutId_ReturnsUnscrapped() {
        var pattern = PatternFixture.createPatternWithId(10L);

        var response = responseFactory.createAll(List.of(pattern), UserFixture.createUser()).getFirst();

        assertThat(response.my().scrapped()).isFalse();
        verifyNoInteractions(scrapRepository);
    }

    @Test
    @DisplayName("목록의 도안 ID만 한 번 조회하고 입력 순서와 도안별 찜 여부를 유지해야 한다")
    void createAll_MultiplePatterns_BatchesScrapsAndPreservesOrder() {
        var user = UserFixture.createUserWithId(1L);
        var first = PatternFixture.createPatternWithId(30L);
        var second = PatternFixture.createPatternWithId(10L);
        var third = PatternFixture.createPatternWithId(20L);
        when(scrapRepository.findScrappedPatternIds(1L, List.of(30L, 10L, 20L)))
                .thenReturn(Set.of(10L, 20L));

        var responses = responseFactory.createAll(List.of(first, second, third), user);

        assertThat(responses).extracting(item -> item.id()).containsExactly(30L, 10L, 20L);
        assertThat(responses).extracting(item -> item.my().scrapped()).containsExactly(false, true, true);
        verify(scrapRepository).findScrappedPatternIds(1L, List.of(30L, 10L, 20L));
        verifyNoMoreInteractions(scrapRepository);
    }

    @Test
    @DisplayName("찜한 도안이 없으면 로그인 사용자도 모든 도안을 미찜으로 반환해야 한다")
    void createAll_NoScraps_ReturnsUnscrapped() {
        var user = UserFixture.createUserWithId(1L);
        var first = PatternFixture.createPatternWithId(10L);
        var second = PatternFixture.createPatternWithId(20L);
        when(scrapRepository.findScrappedPatternIds(1L, List.of(10L, 20L))).thenReturn(Set.of());

        var responses = responseFactory.createAll(List.of(first, second), user);

        assertThat(responses).hasSize(2).allSatisfy(item -> assertThat(item.my().scrapped()).isFalse());
    }

    @Test
    @DisplayName("빈 도안 목록이면 찜과 이미지 서비스를 호출하지 않아야 한다")
    void createAll_EmptyPatterns_SkipsDependencies() {
        var responses = responseFactory.createAll(List.of(), UserFixture.createUserWithId(1L));

        assertThat(responses).isEmpty();
        verifyNoInteractions(scrapRepository, imageService);
    }
}
