package com.ufo.ufo.domain.attendance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.attendance.application.AttendanceService;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceCheckResponse;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceStatusResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("출석 컨트롤러 테스트")
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    @Test
    @DisplayName("출석 체크 API는 서비스 응답을 data에 담아 반환해야 한다")
    void check_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(attendanceService.check(user))
                .thenReturn(new AttendanceCheckResponse(LocalDate.of(2026, 2, 22), true, 1, 12));

        ResponseEntity<ApiResponse<AttendanceCheckResponse>> response = attendanceController.check(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().date()).isEqualTo(LocalDate.of(2026, 2, 22));
        assertThat(response.getBody().data().rewarded()).isTrue();
        assertThat(response.getBody().data().rewardAmount()).isEqualTo(1);
        assertThat(response.getBody().data().balance()).isEqualTo(12);
        assertThat(response.getBody().error()).isNull();
        verify(attendanceService).check(user);
    }

    @Test
    @DisplayName("출석 현황 API는 서비스 응답의 날짜별 출석 여부를 data에 담아 반환해야 한다")
    void getStatus_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        Integer year = 2026;
        Integer month = 2;
        when(attendanceService.getStatus(user, year, month))
                .thenReturn(new AttendanceStatusResponse(
                        Map.of("2026-02-01", true, "2026-02-02", false)));

        ResponseEntity<ApiResponse<AttendanceStatusResponse>> response = attendanceController.getStatus(user, year, month);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().rewarded().get("2026-02-01")).isTrue();
        assertThat(response.getBody().data().rewarded().get("2026-02-02")).isFalse();
        assertThat(response.getBody().error()).isNull();
        verify(attendanceService).getStatus(user, year, month);
    }
}
