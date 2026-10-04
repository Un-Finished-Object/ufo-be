package com.ufo.ufo.domain.attendance.dao;

import com.ufo.ufo.domain.attendance.domain.AttendanceCheck;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceCheckRepository extends JpaRepository<AttendanceCheck, Long> {

    Optional<AttendanceCheck> findByUser_IdAndAttendanceDate(Long userId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AttendanceCheck a where a.user.id = :userId and a.attendanceDate = :date")
    Optional<AttendanceCheck> findByUserAndDateForUpdate(@Param("userId") Long userId,
            @Param("date") LocalDate date);

    List<AttendanceCheck> findAllByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId, LocalDate from, LocalDate to);
}
