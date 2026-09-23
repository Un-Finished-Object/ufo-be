package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.PatternViewLogRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.global.exception.UnauthorizedUserException;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 조회수 서비스 테스트")
class PatternViewServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternViewLogRepository patternViewLogRepository;

    @InjectMocks
    private PatternViewService patternViewService;

    @Test
    @DisplayName("첫 조회는 조회수를 증가시키고 조회 기록을 저장한다")
    void increaseViewCount_FirstView_IncreasesAndSavesLog() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        var response = patternViewService.increaseViewCount(user, 10L);

        assertThat(response.viewCount()).isEqualTo(1);
        assertThat(pattern.getViewCount()).isEqualTo(1);
        verify(patternViewLogRepository).save(any(PatternViewLog.class));
    }

    @Test
    @DisplayName("같은 날 다시 조회하면 조회수를 증가시키지 않는다")
    void increaseViewCount_DuplicateView_DoesNotIncrease() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternViewLogRepository.existsByPattern_IdAndUser_IdAndViewedDate(eq(10L), eq(1L), any(LocalDate.class)))
                .thenReturn(true);

        var response = patternViewService.increaseViewCount(user, 10L);

        assertThat(response.viewCount()).isZero();
        assertThat(pattern.getViewCount()).isZero();
        verify(patternViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 도안의 조회수 증가는 실패한다")
    void increaseViewCount_NotFound_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patternViewService.increaseViewCount(user, 99L))
                .isInstanceOf(PatternNotFoundException.class);
        verifyNoInteractions(patternViewLogRepository);
    }

    @Test
    @DisplayName("삭제된 도안의 조회수 증가는 실패한다")
    void increaseViewCount_DeletedPattern_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(pattern, LocalDateTime.now());
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> patternViewService.increaseViewCount(user, 10L))
                .isInstanceOf(PatternNotFoundException.class);
        verifyNoInteractions(patternViewLogRepository);
    }

    @Test
    @DisplayName("비로그인 사용자는 조회수를 증가시킬 수 없다")
    void increaseViewCount_AnonymousUser_ThrowsException() {
        assertThatThrownBy(() -> patternViewService.increaseViewCount(null, 10L))
                .isInstanceOf(UnauthorizedUserException.class);
        verifyNoInteractions(patternRepository, patternViewLogRepository);
    }

    @Test
    @DisplayName("ID가 없는 사용자는 조회수를 증가시킬 수 없다")
    void increaseViewCount_UserWithoutId_ThrowsException() {
        var user = UserFixture.createUser();

        assertThatThrownBy(() -> patternViewService.increaseViewCount(user, 10L))
                .isInstanceOf(UnauthorizedUserException.class);
        verifyNoInteractions(patternRepository, patternViewLogRepository);
    }
}
