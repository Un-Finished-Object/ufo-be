package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PatternAlternativeYarnRepository extends JpaRepository<PatternAlternativeYarn, Long> {
    @EntityGraph(attributePaths = {"yarn", "user", "gauges"})
    List<PatternAlternativeYarn> findAllByPattern_IdOrderByIdAsc(Long patternId);

    Optional<PatternAlternativeYarn> findByIdAndPattern_Id(Long id, Long patternId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatternAlternativeYarn> findById(Long id);
}
