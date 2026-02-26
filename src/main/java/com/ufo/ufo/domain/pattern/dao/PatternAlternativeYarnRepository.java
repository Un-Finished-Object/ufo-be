package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternAlternativeYarnRepository extends JpaRepository<PatternAlternativeYarn, Long> {
    List<PatternAlternativeYarn> findAllByPattern_IdOrderByIdAsc(Long patternId);

    Optional<PatternAlternativeYarn> findByIdAndPattern_Id(Long id, Long patternId);
}
