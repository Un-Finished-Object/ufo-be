package com.ufo.ufo.domain.attendance.dto.response;

import java.time.LocalDate;

public record AttendanceCheckResponse(
        LocalDate date,
        boolean rewarded,
        int rewardAmount,
        int balance
) {
}
