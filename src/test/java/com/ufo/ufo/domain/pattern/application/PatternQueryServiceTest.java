package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternImage;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternSubCategoryNotAllowedException;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 조회 서비스 테스트")
class PatternQueryServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternImageRepository patternImageRepository;

    @Mock
    private ScrapRepository scrapRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private PatternListItemResponseFactory responseFactory;

    @InjectMocks
    private PatternQueryService patternQueryService;

    @Test
    @DisplayName("도안 목록과 페이지 정보를 반환한다")
    void getPatterns_ReturnsItemsAndPage() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternListItemResponse item = PatternListItemResponse.from(pattern, true, "thumbnail");
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pattern)));
        when(responseFactory.create(pattern, user)).thenReturn(item);

        PatternListResponse response = patternQueryService.getPatterns(user, "all", null, "news", 1);

        assertThat(response.items()).containsExactly(item);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isZero();
    }

    @Test
    @DisplayName("category가 all이면 카테고리 조건 없이 조회한다")
    void getPatterns_AllCategory_UsesNullFilter() {
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class))).thenReturn(Page.empty());

        patternQueryService.getPatterns(null, "all", null, "news", 1);

        verify(patternRepository).findAllByCategory(eq(null), eq(null), any(PageRequest.class));
    }

    @Test
    @DisplayName("subCategory가 all이면 하위 카테고리 조건 없이 조회한다")
    void getPatterns_AllSubCategory_UsesNullFilter() {
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class))).thenReturn(Page.empty());

        patternQueryService.getPatterns(null, "apparel", "all", "news", 1);

        verify(patternRepository).findAllByCategory(eq("apparel"), eq(null), any(PageRequest.class));
    }

    @Test
    @DisplayName("scraps 정렬은 찜 개수 기준 조회를 사용한다")
    void getPatterns_ScrapsSort_UsesPopularityQuery() {
        when(patternRepository.findAllByCategoryOrderByPopularity(any(), any(), any(PageRequest.class)))
                .thenReturn(Page.empty());

        patternQueryService.getPatterns(null, "all", null, "scraps", 1);

        verify(patternRepository).findAllByCategoryOrderByPopularity(any(), any(), any(PageRequest.class));
        verify(patternRepository, never()).findAllByCategory(any(), any(), any(PageRequest.class));
    }

    @Test
    @DisplayName("views 정렬은 조회수 내림차순을 사용한다")
    void getPatterns_ViewsSort_UsesViewCountDescending() {
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class))).thenReturn(Page.empty());

        patternQueryService.getPatterns(null, "all", null, "views", 1);

        verify(patternRepository).findAllByCategory(any(), any(), argThat(pageable -> {
            Sort.Order order = pageable.getSort().getOrderFor("viewCount");
            return order != null && order.isDescending();
        }));
    }

    @Test
    @DisplayName("의류가 아닌 카테고리에 subCategory가 있으면 예외가 발생한다")
    void getPatterns_SubCategoryForNonApparel_ThrowsException() {
        assertThatThrownBy(() -> patternQueryService.getPatterns(null, "bags", "all", "news", 1))
                .isInstanceOf(PatternSubCategoryNotAllowedException.class);
        verifyNoInteractions(patternRepository);
    }

    @Test
    @DisplayName("검색어가 null이면 빈 문자열로 검색한다")
    void searchPatterns_NullKeyword_UsesEmptyKeyword() {
        when(patternRepository.search(eq(""), any(PageRequest.class))).thenReturn(Page.empty());

        PatternListResponse response = patternQueryService.searchPatterns(null, null, 1);

        assertThat(response.items()).isEmpty();
        verify(patternRepository).search(eq(""), any(PageRequest.class));
    }

    @Test
    @DisplayName("검색 결과의 남은 페이지 수는 최대 5로 제한한다")
    void searchPatterns_ManyPages_LimitsNextPageToFive() {
        Page<Pattern> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 200);
        when(patternRepository.search(eq("니트"), any(PageRequest.class))).thenReturn(page);

        PatternListResponse response = patternQueryService.searchPatterns(null, "니트", 1);

        assertThat(response.nextPage()).isEqualTo(5);
    }

    @Test
    @DisplayName("상세 조회는 로그인 사용자의 찜 여부를 반환한다")
    void getPatternDetail_LoggedInUser_ReturnsScrapStatus() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        when(patternRepository.findDetailById(2L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 2L)).thenReturn(true);
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn/patterns/1.png");

        PatternDetailResponse response = patternQueryService.getPatternDetail(user, 2L);

        assertThat(response.my().scrapped()).isTrue();
    }

    @Test
    @DisplayName("상세 이미지가 없으면 썸네일을 사용한다")
    void getPatternDetail_NoImages_UsesThumbnail() {
        Pattern pattern = PatternFixture.createPattern("pattern", "artist", "apparel", "vest", "patterns/t.png");
        PatternFixture.setId(pattern, 2L);
        when(patternRepository.findDetailById(2L)).thenReturn(Optional.of(pattern));
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());
        when(imageService.buildImageUrl("patterns/t.png")).thenReturn("https://cdn/patterns/t.png");

        PatternDetailResponse response = patternQueryService.getPatternDetail(null, 2L);

        assertThat(response.images()).containsExactly("https://cdn/patterns/t.png");
    }

    @Test
    @DisplayName("상세 이미지가 있으면 썸네일 대신 이미지 목록을 사용한다")
    void getPatternDetail_WithImages_UsesImageList() {
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        PatternImage first = PatternImage.builder().pattern(pattern).imageUrl("patterns/first.png").imageOrder(1).build();
        PatternImage second = PatternImage.builder().pattern(pattern).imageUrl("patterns/second.png").imageOrder(2).build();
        when(patternRepository.findDetailById(2L)).thenReturn(Optional.of(pattern));
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L))
                .thenReturn(List.of(first, second));
        when(imageService.buildImageUrl("patterns/first.png")).thenReturn("https://cdn/first.png");
        when(imageService.buildImageUrl("patterns/second.png")).thenReturn("https://cdn/second.png");

        PatternDetailResponse response = patternQueryService.getPatternDetail(null, 2L);

        assertThat(response.images()).containsExactly("https://cdn/first.png", "https://cdn/second.png");
        verify(imageService, never()).buildImageUrl(pattern.getThumbnailUrl());
    }

    @Test
    @DisplayName("상세 조회는 원작 실 세트를 포함한다")
    void getPatternDetail_WithOriginalYarns_ReturnsYarnSets() {
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        Yarn main = YarnFixture.createYarnWithId(10L);
        Yarn second = YarnFixture.createYarnWithId(11L);
        PatternOriginalYarn set = PatternFixture.setOriginalYarn(pattern, main, second, null);
        PatternFixture.setOriginalYarnId(set, 100L);
        when(patternRepository.findDetailById(2L)).thenReturn(Optional.of(pattern));
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn/patterns/1.png");

        PatternDetailResponse response = patternQueryService.getPatternDetail(null, 2L);

        assertThat(response.meta().originalYarn()).hasSize(1);
        assertThat(response.meta().originalYarn().getFirst().originalYarnSetId()).isEqualTo(100L);
        assertThat(response.meta().originalYarn().getFirst().firstYarn().yarnId()).isEqualTo(10L);
        assertThat(response.meta().originalYarn().getFirst().secondYarn().yarnId()).isEqualTo(11L);
        assertThat(response.meta().originalYarn().getFirst().subYarn()).isNull();
    }

    @Test
    @DisplayName("비로그인 상세 조회는 찜 여부를 false로 반환한다")
    void getPatternDetail_AnonymousUser_ReturnsUnscrapped() {
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        when(patternRepository.findDetailById(2L)).thenReturn(Optional.of(pattern));
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());
        when(imageService.buildImageUrl(pattern.getThumbnailUrl())).thenReturn("https://cdn/patterns/1.png");

        PatternDetailResponse response = patternQueryService.getPatternDetail(null, 2L);

        assertThat(response.my().scrapped()).isFalse();
        verifyNoInteractions(scrapRepository);
    }

    @Test
    @DisplayName("존재하지 않는 도안 상세 조회는 예외가 발생한다")
    void getPatternDetail_NotFound_ThrowsException() {
        when(patternRepository.findDetailById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patternQueryService.getPatternDetail(null, 99L))
                .isInstanceOf(PatternNotFoundException.class);
    }
}
