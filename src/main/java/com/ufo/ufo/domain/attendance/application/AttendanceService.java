package com.ufo.ufo.domain.attendance.application;

import com.ufo.ufo.domain.attendance.dao.AttendanceCheckRepository;
import com.ufo.ufo.domain.attendance.domain.AttendanceCheck;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceCheckResponse;
import com.ufo.ufo.domain.attendance.dto.response.AttendanceStatusResponse;
import com.ufo.ufo.domain.attendance.exception.InvalidAttendanceYearMonthException;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceCheckRepository attendanceCheckRepository;
    private final UserService userService;
    private final CreditService creditService;

    @Transactional
    public AttendanceCheckResponse check(User user) {
        User loginUser = userService.getUserById(user.getId());
        LocalDate today = LocalDate.now();

        if (isAlreadyCheckedToday(loginUser, today)) {
            return createAlreadyCheckedResponse(loginUser, today);
        }

        saveAttendanceCheck(loginUser, today);
        rewardAttendance(loginUser);
        return createSuccessResponse(loginUser, today);
    }

    public AttendanceStatusResponse getStatus(User user, Integer year, Integer month) {
        User loginUser = userService.getUserById(user.getId());
        YearMonth targetMonth = resolveYearMonth(year, month);
        LocalDate from = targetMonth.atDay(1);
        LocalDate to = targetMonth.atEndOfMonth();
        Set<LocalDate> checkedDates = findCheckedDates(loginUser, from, to);
        return new AttendanceStatusResponse(buildRewardedMap(from, to, checkedDates));
    }

    private boolean isAlreadyCheckedToday(User user, LocalDate date) {
        return attendanceCheckRepository.findByUser_IdAndAttendanceDate(user.getId(), date).isPresent();
    }

    private AttendanceCheckResponse createAlreadyCheckedResponse(User user, LocalDate date) {
        return new AttendanceCheckResponse(date, false, 0, user.getBallBalance());
    }

    private void saveAttendanceCheck(User user, LocalDate date) {
        attendanceCheckRepository.save(AttendanceCheck.builder()
                .user(user)
                .attendanceDate(date)
                .build());
    }

    private void rewardAttendance(User user) {
        creditService.addCredits(user, CreditPolicy.ATTENDANCE_DAILY_BALLS, CreditTransactionType.ATTENDANCE_DAILY);
    }

    private AttendanceCheckResponse createSuccessResponse(User user, LocalDate date) {
        return new AttendanceCheckResponse(date, true, CreditPolicy.ATTENDANCE_DAILY_BALLS, user.getBallBalance());
    }

    private YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            throw new InvalidAttendanceYearMonthException();
        }
        try {
            return YearMonth.of(year, month);
        } catch (RuntimeException e) {
            throw new InvalidAttendanceYearMonthException();
        }
    }

    private Set<LocalDate> findCheckedDates(User user, LocalDate from, LocalDate to) {
        return attendanceCheckRepository.findAllByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateAsc(user.getId(), from, to)
                .stream()
                .map(AttendanceCheck::getAttendanceDate)
                .collect(Collectors.toSet());
    }

    private Map<String, Boolean> buildRewardedMap(LocalDate from, LocalDate to, Set<LocalDate> checkedDates) {
        Map<String, Boolean> rewarded = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            rewarded.put(date.toString(), checkedDates.contains(date));
        }
        return rewarded;
    }
}
