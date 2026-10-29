package com.ufo.ufo.domain.image.dao;

import com.ufo.ufo.domain.image.dao.projection.ProfileImageTaskId;
import com.ufo.ufo.domain.image.dao.projection.ProfileImageTaskOwner;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProfileImageTaskRepository extends JpaRepository<ProfileImageTask, Long> {

    Optional<ProfileImageTaskOwner> findOwnerById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProfileImageTask> findLockedById(Long id);

    List<ProfileImageTaskId> findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            LocalDateTime now, Pageable pageable);
}
