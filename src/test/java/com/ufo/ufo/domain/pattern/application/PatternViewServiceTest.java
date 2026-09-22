package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.PatternViewLogRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import com.ufo.ufo.domain.pattern.dto.response.PatternViewCountResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
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
    @DisplayName("첫 조회는 조회수를 증가시키고 조회 기록을 저장해야 한다")
    void increaseViewCount_FirstView_IncreasesAndSavesLog() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(patternViewLogRepository.existsByPattern_IdAndUser_IdAndViewedDate(10L, 1L, LocalDate.now()))
                .thenReturn(false);

        PatternViewCountResponse response = patternViewService.increaseViewCount(user, 10L);

        assertThat(response.viewCount()).isEqualTo(1);
        verify(patternViewLogRepository).save(any(PatternViewLog.class));
    }
}
