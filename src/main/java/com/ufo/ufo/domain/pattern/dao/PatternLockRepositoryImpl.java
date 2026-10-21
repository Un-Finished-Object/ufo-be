package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class PatternLockRepositoryImpl implements PatternLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Optional<Pattern> findByIdForUpdate(Long patternId) {
        Pattern pattern = entityManager.find(Pattern.class, patternId);
        if (pattern == null) {
            return Optional.empty();
        }
        LockModeType lockMode = entityManager.getLockMode(pattern);
        if (lockMode != LockModeType.PESSIMISTIC_WRITE
                && lockMode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
            try {
                // 락 조회만으로는 이미 관리 중인 도안의 오래된 카운터가 갱신되지 않는다.
                entityManager.refresh(pattern, LockModeType.PESSIMISTIC_WRITE);
            } catch (EntityNotFoundException exception) {
                return Optional.empty();
            }
        }
        // 같은 트랜잭션의 재호출은 미저장 조회수·찜 수 변경을 유지한다.
        return Optional.of(pattern);
    }
}
