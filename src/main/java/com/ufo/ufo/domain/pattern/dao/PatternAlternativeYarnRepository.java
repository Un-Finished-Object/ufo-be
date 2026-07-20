package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatternAlternativeYarnRepository extends JpaRepository<PatternAlternativeYarn, Long> {
    Optional<PatternAlternativeYarn> findByIdAndPattern_Id(Long id, Long patternId);

    @Query("""
            select a
            from PatternAlternativeYarn a
            join fetch a.yarn
            join fetch a.user
            where a.pattern.id = :patternId
              and a.deletedAt is null
            order by a.ranking asc, a.id asc
            """)
    List<PatternAlternativeYarn> findActiveByPatternId(@Param("patternId") Long patternId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatternAlternativeYarn> findById(Long id);
}
