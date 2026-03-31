package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativesResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.ApiException;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternAlternativeYarnFixture;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 서비스 테스트")
class PatternServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private ScrapRepository scrapRepository;

    @Mock
    private PatternImageRepository patternImageRepository;

    @Mock
    private PatternAlternativeYarnRepository patternAlternativeYarnRepository;

    @Mock
    private YarnRepository yarnRepository;

    @InjectMocks
    private PatternService patternService;

    @Test
    @DisplayName("도안 목록 조회는 도안 아이템 목록과 페이지를 반환해야 한다")
    void getPatterns_ReturnsItemsAndPage() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(1L);
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pattern)));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 1L)).thenReturn(true);

        PatternListResponse response = patternService.getPatterns(user, "apparel", "sweater", "news", 1);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(1L);
        assertThat(response.items().getFirst().category()).isEqualTo("apparel");
        assertThat(response.items().getFirst().subCategory()).isEqualTo("sweater");
        assertThat(response.items().getFirst().stats().scraps()).isEqualTo(0L);
        assertThat(response.items().getFirst().my().scrapped()).isTrue();
    }

    @Test
    @DisplayName("도안 목록 조회에서 category가 all이면 카테고리 필터 없이 조회해야 한다")
    void getPatterns_AllCategory_UsesNullCategoryFilter() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(1L);
        when(patternRepository.findAllByCategory(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pattern)));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 1L)).thenReturn(false);

        PatternListResponse response = patternService.getPatterns(user, "all", null, "news", 1);

        assertThat(response.items()).hasSize(1);
        verify(patternRepository).findAllByCategory(eq(null), eq(null), any(PageRequest.class));
    }

    @Test
    @DisplayName("도안 목록 조회에서 sort가 scraps면 인기순 조회 리포지토리를 사용해야 한다")
    void getPatterns_ScrapsSort_UsesPopularityQuery() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(1L);
        when(patternRepository.findAllByCategoryOrderByPopularity(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pattern)));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 1L)).thenReturn(false);

        PatternListResponse response = patternService.getPatterns(user, "all", null, "scraps", 1);

        assertThat(response.items()).hasSize(1);
        verify(patternRepository).findAllByCategoryOrderByPopularity(any(), any(), any(PageRequest.class));
    }

    @Test
    @DisplayName("도안 상세 조회는 찜 여부를 포함해 반환해야 한다")
    void getPatternDetail_ReturnsScrapFlag() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        when(patternRepository.findById(2L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 2L)).thenReturn(true);
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());

        PatternDetailResponse response = patternService.getPatternDetail(user, 2L);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.my().scrapped()).isTrue();
        assertThat(response.author()).isEqualTo("artist");
        assertThat(response.stats().views()).isEqualTo(1);
    }

    @Test
    @DisplayName("도안 상세 조회에서 이미지가 없으면 썸네일을 images에 포함해야 한다")
    void getPatternDetail_UsesThumbnailWhenImageEmpty() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPattern("patternA", "artist", "apparel", "sweater", "./patterns/t.png");
        PatternFixture.setId(pattern, 2L);
        when(patternRepository.findById(2L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 2L)).thenReturn(false);
        when(patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(2L)).thenReturn(List.of());

        PatternDetailResponse response = patternService.getPatternDetail(user, 2L);

        assertThat(response.images()).containsExactly("./patterns/t.png");
    }

    @Test
    @DisplayName("삭제된 도안 상세 조회는 예외가 발생해야 한다")
    void getPatternDetail_DeletedPattern_ThrowsNotFound() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(2L);
        PatternFixture.setDeletedAt(pattern, java.time.LocalDateTime.now());
        when(patternRepository.findById(2L)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> patternService.getPatternDetail(user, 2L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("게스트는 대체 실 등록 권한이 없어 예외가 발생해야 한다")
    void createAlternative_Guest_ThrowsForbidden() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 1L);

        assertThatThrownBy(() -> patternService.createAlternative(
                guest,
                1L,
                new CreateAlternativeRequest("name", "./yarns/1.png", 100, 10000, "gauge", "store")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("대체 실 수정은 Yarn 정보와 추천 정보를 갱신해야 한다")
    void updateAlternative_UpdatesAlternativeAndYarn() {
        User user = UserFixture.createUser("u@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        Yarn yarn = YarnFixture.createYarnWithId(20L);
        PatternAlternativeYarn alt = PatternAlternativeYarnFixture.createWithId(30L, pattern, user, yarn);

        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L)).thenReturn(Optional.of(alt));

        var response = patternService.updateAlternative(
                user,
                10L,
                30L,
                new UpdateAlternativeYarnRequest("newName", "./yarns/new.png", 120, 20000, "newGauge", "newStore")
        );

        assertThat(response.altId()).isEqualTo(30L);
        assertThat(response.yarnName()).isEqualTo("newName");
        assertThat(response.cost()).isEqualTo(20000);
        assertThat(response.weight()).isEqualTo(120);
    }

    @Test
    @DisplayName("자신이 등록하지 않은 대체 실은 수정할 수 없어야 한다")
    void updateAlternative_NotOwner_ThrowsForbidden() {
        User requester = UserFixture.createUser("u@example.com", Role.ROLE_USER);
        UserFixture.setId(requester, 1L);
        User owner = UserFixture.createUser("owner@example.com", Role.ROLE_USER);
        UserFixture.setId(owner, 2L);

        Pattern pattern = PatternFixture.createPatternWithId(10L);
        Yarn yarn = YarnFixture.createYarnWithId(20L);
        PatternAlternativeYarn alt = PatternAlternativeYarnFixture.createWithId(30L, pattern, owner, yarn);

        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L)).thenReturn(Optional.of(alt));

        assertThatThrownBy(() -> patternService.updateAlternative(
                requester, 10L, 30L, new UpdateAlternativeYarnRequest("newName", "./yarns/new.png", 120, 20000, "newGauge", "newStore")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("대체 실 조회는 도안 활성 여부를 확인하고 목록을 반환해야 한다")
    void getAlternatives_ReturnsAlternativesWithoutCreditCharge() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternAlternativeYarnRepository.findAllByPattern_IdOrderByIdAsc(10L)).thenReturn(List.of());

      PatternAlternativesResponse response = patternService.getAlternatives(user, 10L);

      assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("도안 검색에서 keyword가 null이면 빈 문자열로 검색해야 한다")
    void searchPatterns_KeywordNull_UsesEmptyKeyword() {
        User user = UserFixture.createUserWithId(1L);
        when(patternRepository.search(eq(""), any(PageRequest.class)))
                .thenReturn(Page.empty());

        PatternListResponse response = patternService.searchPatterns(user, null, 1);

        assertThat(response.items()).isEmpty();
        verify(patternRepository).search(eq(""), any(PageRequest.class));
    }

    @Test
    @DisplayName("추천 도안 조회는 유저 관심사와 매칭된 도안을 우선 반환해야 한다")
    void getRecommendedPatterns_ReturnsInterestMatchedFirst() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPattern("빈티지 니트", "artist", "apparel", "sweater", "./patterns/1.png");
        PatternFixture.setId(pattern, 11L);

        when(patternRepository.findRecommendedByUserInterest(1L)).thenReturn(List.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 11L)).thenReturn(false);

        PatternItemsResponse response = patternService.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(11L);
    }

    @Test
    @DisplayName("추천 도안 조회에서 관심사가 없으면 기본 추천 목록을 반환해야 한다")
    void getRecommendedPatterns_NoInterest_ReturnsDefaultRecommend() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPattern("기본 추천", "artist", "apparel", "sweater", "./patterns/1.png");
        PatternFixture.setId(pattern, 12L);
        when(patternRepository.findRecommendedByUserInterest(1L)).thenReturn(List.of());
        when(patternRepository.findRecommended()).thenReturn(List.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 12L)).thenReturn(false);

        PatternItemsResponse response = patternService.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(12L);
        verify(patternRepository).findRecommended();
    }

    @Test
    @DisplayName("추천 도안 조회에서 관심사 매칭 결과가 없으면 기본 추천 목록으로 fallback해야 한다")
    void getRecommendedPatterns_EmptyMatched_FallbackToDefaultRecommend() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPattern("fallback 추천", "artist", "apparel", "sweater", "./patterns/1.png");
        PatternFixture.setId(pattern, 13L);
        when(patternRepository.findRecommendedByUserInterest(1L)).thenReturn(List.of());
        when(patternRepository.findRecommended()).thenReturn(List.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 13L)).thenReturn(false);

        PatternItemsResponse response = patternService.getRecommendedPatterns(user);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(13L);
        verify(patternRepository).findRecommendedByUserInterest(1L);
        verify(patternRepository).findRecommended();
    }

    @Test
    @DisplayName("게스트는 대체 실 삭제 권한이 없어 예외가 발생해야 한다")
    void deleteAlternative_Guest_ThrowsForbidden() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 1L);

        assertThatThrownBy(() -> patternService.deleteAlternative(guest, 10L, 1L))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(patternRepository);
    }

    @Test
    @DisplayName("자신이 등록하지 않은 대체 실은 삭제할 수 없어야 한다")
    void deleteAlternative_NotOwner_ThrowsForbidden() {
        User requester = UserFixture.createUser("u@example.com", Role.ROLE_USER);
        UserFixture.setId(requester, 1L);
        User owner = UserFixture.createUser("owner@example.com", Role.ROLE_USER);
        UserFixture.setId(owner, 2L);

        Pattern pattern = PatternFixture.createPatternWithId(10L);
        Yarn yarn = YarnFixture.createYarnWithId(20L);
        PatternAlternativeYarn alt = PatternAlternativeYarnFixture.createWithId(30L, pattern, owner, yarn);

        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L)).thenReturn(Optional.of(alt));

        assertThatThrownBy(() -> patternService.deleteAlternative(requester, 10L, 30L))
                .isInstanceOf(ApiException.class);
    }

}
