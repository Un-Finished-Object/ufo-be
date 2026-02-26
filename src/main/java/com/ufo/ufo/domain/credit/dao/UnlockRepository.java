package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnlockRepository extends JpaRepository<Unlock, Long> {

    boolean existsByUser_IdAndPatternIdAndType(Long userId, Long patternId, UnlockType type);
}
