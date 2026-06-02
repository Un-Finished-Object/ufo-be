package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnlockRepository extends JpaRepository<Unlock, Long> {

    boolean existsByUser_IdAndPatternIdAndType(Long userId, Long patternId, UnlockType type);

    List<Unlock> findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(Long userId, UnlockType type);
}
