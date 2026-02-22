package com.ufo.ufo.domain.attendance.dao;

import com.ufo.ufo.domain.attendance.domain.AttendanceCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCheckRepository extends JpaRepository<AttendanceCheck, Long> {

    Optional<AttendanceCheck> findByUser_IdAndAttendanceDate(Long userId, LocalDate date);

    List<AttendanceCheck> findAllByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId, LocalDate from, LocalDate to);
}
