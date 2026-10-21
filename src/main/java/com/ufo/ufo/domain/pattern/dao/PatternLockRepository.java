package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.util.Optional;

public interface PatternLockRepository {

    Optional<Pattern> findByIdForUpdate(Long patternId);
}
