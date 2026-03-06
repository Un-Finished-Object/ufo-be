package com.ufo.ufo.domain.attendance.api;

import com.ufo.ufo.domain.attendance.application.AttendanceService;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceCheckResponse;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceStatusResponse;
import com.ufo.ufo.domain.attendance.validation.ValidMonth;
import com.ufo.ufo.domain.attendance.validation.ValidYear;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
@Validated
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AttendanceCheckResponse>> check(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.check(user)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AttendanceStatusResponse>> getStatus(
            @LoginUser User user,
            @RequestParam @ValidYear Integer year,
            @RequestParam @ValidMonth Integer month
    ) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getStatus(user, year, month)));
    }
}
