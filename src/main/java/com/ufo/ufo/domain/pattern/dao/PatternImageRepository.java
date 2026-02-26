package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.PatternImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternImageRepository extends JpaRepository<PatternImage, Long> {
    List<PatternImage> findAllByPattern_IdOrderByImageOrderAscIdAsc(Long patternId);
}
