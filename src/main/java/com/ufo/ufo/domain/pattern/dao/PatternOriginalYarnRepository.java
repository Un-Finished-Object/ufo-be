package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatternOriginalYarnRepository extends JpaRepository<PatternOriginalYarn, Long> {

    @Query("""
            select originalYarn
            from PatternOriginalYarn originalYarn
            join originalYarn.pattern pattern
            join fetch originalYarn.mainYarn
            left join fetch originalYarn.secondYarn
            left join fetch originalYarn.subYarn
            where originalYarn.id = :originalYarnSetId
              and pattern.deletedAt is null
            """)
    Optional<PatternOriginalYarn> findActiveSetById(@Param("originalYarnSetId") Long originalYarnSetId);
}
