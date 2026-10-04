package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UnlockRepository extends JpaRepository<Unlock, Long> {

    boolean existsByUser_IdAndPatternIdAndType(Long userId, Long patternId, UnlockType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Unlock> findByUser_IdAndPatternIdAndType(Long userId, Long patternId, UnlockType type);

    List<Unlock> findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(Long userId, UnlockType type);
}
