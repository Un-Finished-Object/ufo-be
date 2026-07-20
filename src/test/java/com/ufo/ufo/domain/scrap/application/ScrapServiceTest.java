package com.ufo.ufo.domain.scrap.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.scrap.domain.Scrap;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse;
import com.ufo.ufo.domain.scrap.dto.response.PatternScrapResponse;
import com.ufo.ufo.global.exception.ApiException;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("찜 서비스 테스트")
class ScrapServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private ScrapRepository scrapRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ScrapService scrapService;

    @BeforeEach
    void setUp() {
        lenient().when(imageService.buildImageUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("도안 찜 추가는 중복이 아니면 찜을 저장하고 도안 찜 카운트를 증가시켜야 한다")
    void addPatternScrap_SavesAndIncreasesCount() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        setScrapsCount(pattern, 3);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 10L)).thenReturn(false);

        PatternScrapResponse response = scrapService.addPatternScrap(user, 10L);

        assertThat(response.scrapped()).isTrue();
        assertThat(response.scrapCount()).isEqualTo(4);
        assertThat(pattern.getScrapsCount()).isEqualTo(4);
        verify(scrapRepository).save(any(Scrap.class));
    }

    @Test
    @DisplayName("도안 찜 추가는 이미 찜한 경우 중복 저장하지 않아야 한다")
    void addPatternScrap_AlreadyScrapped_DoesNotSave() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        setScrapsCount(pattern, 3);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.existsByUser_IdAndPattern_Id(1L, 10L)).thenReturn(true);

        PatternScrapResponse response = scrapService.addPatternScrap(user, 10L);

        assertThat(response.scrapped()).isTrue();
        assertThat(response.scrapCount()).isEqualTo(3);
        assertThat(pattern.getScrapsCount()).isEqualTo(3);
        verify(scrapRepository, never()).save(any(Scrap.class));
    }

    @Test
    @DisplayName("도안 찜 취소는 찜이 존재하면 삭제하고 도안 찜 카운트를 감소시켜야 한다")
    void removePatternScrap_DeletesAndDecreasesCount() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        setScrapsCount(pattern, 2);
        Scrap scrap = Scrap.builder().user(user).pattern(pattern).build();
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.findByUser_IdAndPattern_Id(1L, 10L)).thenReturn(Optional.of(scrap));

        PatternScrapResponse response = scrapService.removePatternScrap(user, 10L);

        assertThat(response.scrapped()).isFalse();
        assertThat(response.scrapCount()).isEqualTo(1);
        assertThat(pattern.getScrapsCount()).isEqualTo(1);
        verify(scrapRepository).delete(scrap);
    }

    @Test
    @DisplayName("도안 찜 취소는 찜이 없어도 정상 응답을 반환해야 한다")
    void removePatternScrap_NotExists_ReturnsUnscrapped() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        setScrapsCount(pattern, 2);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(scrapRepository.findByUser_IdAndPattern_Id(1L, 10L)).thenReturn(Optional.empty());

        PatternScrapResponse response = scrapService.removePatternScrap(user, 10L);

        assertThat(response.scrapped()).isFalse();
        assertThat(response.scrapCount()).isEqualTo(2);
        assertThat(pattern.getScrapsCount()).isEqualTo(2);
        verify(scrapRepository, never()).delete(any(Scrap.class));
    }

    @Test
    @DisplayName("내 찜 목록 조회는 찜 생성 최신순으로 도안 목록을 반환해야 한다")
    void getMyScraps_ReturnsLatestOrderItems() {
        var user = UserFixture.createUserWithId(1L);
        Pattern newer = PatternFixture.createPatternWithId(20L);
        Pattern older = PatternFixture.createPatternWithId(10L);
        Scrap newScrap = Scrap.builder().user(user).pattern(newer).build();
        Scrap oldScrap = Scrap.builder().user(user).pattern(older).build();
        when(scrapRepository.findAllByUser_IdAndPattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newScrap, oldScrap)));

        MyScrapsResponse response = scrapService.getMyScraps(user, 1);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).id()).isEqualTo(20L);
        assertThat(response.items().get(0).title()).isEqualTo(newer.getTitle());
        assertThat(response.items().get(0).thumbnailUrl()).isEqualTo(newer.getThumbnailUrl());
        assertThat(response.items().get(0).author()).isEqualTo(newer.getDesigner());
        assertThat(response.items().get(0).my().scrapped()).isTrue();
        assertThat(response.items().get(1).id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("도안이 없으면 찜 추가/취소에서 예외가 발생해야 한다")
    void addOrRemovePatternScrap_PatternNotFound_Throws() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scrapService.addPatternScrap(user, 10L))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> scrapService.removePatternScrap(user, 10L))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(scrapRepository);
    }

    @Test
    @DisplayName("삭제된 도안은 찜 추가/취소할 수 없어 예외가 발생해야 한다")
    void addOrRemovePatternScrap_DeletedPattern_Throws() {
        var user = UserFixture.createUserWithId(1L);
        Pattern deletedPattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(deletedPattern, LocalDateTime.now());
        when(patternRepository.findById(10L)).thenReturn(Optional.of(deletedPattern));

        assertThatThrownBy(() -> scrapService.addPatternScrap(user, 10L))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> scrapService.removePatternScrap(user, 10L))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(scrapRepository);
    }

    private void setScrapsCount(Pattern pattern, int count) {
        try {
            Field scrapsCountField = Pattern.class.getDeclaredField("scrapsCount");
            scrapsCountField.setAccessible(true);
            scrapsCountField.set(pattern, count);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
