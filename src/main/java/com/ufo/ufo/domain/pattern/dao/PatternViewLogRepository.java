package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternViewLogRepository extends JpaRepository<PatternViewLog, Long> {

    boolean existsByPattern_IdAndUser_IdAndViewedDate(Long patternId, Long userId, LocalDate viewedDate);
}
