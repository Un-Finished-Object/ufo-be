package com.ufo.ufo.domain.attendance.exception;

import com.ufo.ufo.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidAttendanceYearMonthException extends ApiException {
    public InvalidAttendanceYearMonthException() {
        super(HttpStatus.BAD_REQUEST, "year와 month는 유효한 연월 값이어야 합니다.");
    }
}
