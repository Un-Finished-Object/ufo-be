package com.ufo.ufo.domain.user.dao;

import com.ufo.ufo.domain.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class UserLockRepositoryImpl implements UserLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Optional<User> findByIdForUpdate(Long userId) {
        User user = entityManager.find(User.class, userId);
        if (user == null) {
            return Optional.empty();
        }
        LockModeType lockMode = entityManager.getLockMode(user);
        if (lockMode != LockModeType.PESSIMISTIC_WRITE
                && lockMode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
            try {
                // A locking query alone does not replace an already managed, stale user.
                entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            } catch (EntityNotFoundException exception) {
                return Optional.empty();
            }
        }
        // Reentrant calls must preserve profile, role and balance changes in this transaction.
        return Optional.of(user);
    }
}
