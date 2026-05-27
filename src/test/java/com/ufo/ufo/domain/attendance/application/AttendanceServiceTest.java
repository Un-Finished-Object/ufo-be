package com.ufo.ufo.domain.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.ufo.ufo.domain.attendance.dao.AttendanceCheckRepository;
import com.ufo.ufo.domain.attendance.domain.AttendanceCheck;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceCheckResponse;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceStatusResponse;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("출석 서비스 테스트")
class AttendanceServiceTest {

    @Mock
    private AttendanceCheckRepository attendanceCheckRepository;

    @Mock
    private UserService userService;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    @DisplayName("당일 첫 출석 체크는 출석 저장과 크레딧 지급을 수행해야 한다")
    void check_FirstCheck_SavesAttendanceAndAddsCredits() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        LocalDate today = LocalDate.now();
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(attendanceCheckRepository.findByUser_IdAndAttendanceDate(1L, today)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            User target = invocation.getArgument(0);
            Integer amount = invocation.getArgument(1);
            target.addCredits(amount);
            return null;
        }).when(creditService).addCredits(loginUser, CreditPolicy.ATTENDANCE_DAILY_BALLS, CreditTransactionType.ATTENDANCE_DAILY);

        AttendanceCheckResponse response = attendanceService.check(requestUser);

        assertThat(response.date()).isEqualTo(today);
        assertThat(response.rewarded()).isTrue();
        assertThat(response.rewardAmount()).isEqualTo(CreditPolicy.ATTENDANCE_DAILY_BALLS);
        assertThat(response.balance()).isEqualTo(CreditPolicy.ATTENDANCE_DAILY_BALLS);
        verify(attendanceCheckRepository).save(any(AttendanceCheck.class));
        verify(creditService).addCredits(loginUser, CreditPolicy.ATTENDANCE_DAILY_BALLS, CreditTransactionType.ATTENDANCE_DAILY);
    }

    @Test
    @DisplayName("당일 이미 출석한 경우 추가 지급 없이 체크 실패 응답을 반환해야 한다")
    void check_AlreadyChecked_ReturnsFalseWithoutSideEffects() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        LocalDate today = LocalDate.now();
        AttendanceCheck existing = AttendanceCheck.builder().user(loginUser).attendanceDate(today).build();
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(attendanceCheckRepository.findByUser_IdAndAttendanceDate(1L, today)).thenReturn(Optional.of(existing));

        AttendanceCheckResponse response = attendanceService.check(requestUser);

        assertThat(response.date()).isEqualTo(today);
        assertThat(response.rewarded()).isFalse();
        assertThat(response.rewardAmount()).isEqualTo(0);
        assertThat(response.balance()).isEqualTo(0);
        verify(attendanceCheckRepository, never()).save(any(AttendanceCheck.class));
        verify(creditService, never()).addCredits(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("출석 현황 조회는 요청한 연월의 1일부터 말일까지 날짜별 출석 여부를 반환해야 한다")
    void getStatus_ReturnsDailyRewardedForRequestedMonth() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        Integer year = 2026;
        Integer month = 2;
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(attendanceCheckRepository.findAllByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                1L, monthStart, monthEnd))
                .thenReturn(List.of(
                        AttendanceCheck.builder().user(loginUser).attendanceDate(monthStart).build()
                ));

        AttendanceStatusResponse response = attendanceService.getStatus(requestUser, year, month);

        assertThat(response.rewarded()).isInstanceOf(LinkedHashMap.class);
        assertThat(response.rewarded()).hasSize(yearMonth.lengthOfMonth());
        assertThat(response.rewarded().get(monthStart.toString())).isTrue();
        if (yearMonth.lengthOfMonth() > 1) {
            assertThat(response.rewarded().get(monthStart.plusDays(1).toString())).isFalse();
        }
        assertThat(response.rewarded()).containsKey(monthEnd.toString());
    }
}
