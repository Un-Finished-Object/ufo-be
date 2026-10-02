package com.ufo.ufo.domain.user.dao;

import com.ufo.ufo.domain.user.domain.User;
import java.util.Optional;

public interface UserLockRepository {

    Optional<User> findByIdForUpdate(Long userId);
}
