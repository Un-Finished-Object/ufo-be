package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.dao.projection.ProfileImageTaskOwner;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.ApiException;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageTaskProcessor {

    private final ProfileImageTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ImageKeyPolicy keyPolicy;
    private final S3ImageObjectManager objectManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void process(Long taskId) {
        Long userId = taskRepository.findOwnerById(taskId)
                .map(ProfileImageTaskOwner::getUserId)
                .orElse(null);
        if (userId == null) {
            return;
        }
        // 프로필 수정과 같은 순서로 잠가 현재 이미지 확인과 S3 삭제 사이의 변경을 막는다.
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        ProfileImageTask task = taskRepository.findLockedById(taskId).orElse(null);
        if (task == null || task.getNextAttemptAt().isAfter(LocalDateTime.now())) {
            return;
        }
        if (user == null || !shouldApply(task, user)) {
            taskRepository.delete(task);
            return;
        }
        try {
            if (task.getTaskType() == ProfileImageTask.Type.LINK) {
                objectManager.markLinked(task.getImageKey());
            } else {
                objectManager.delete(task.getImageKey());
            }
        } catch (RuntimeException failure) {
            task.defer(LocalDateTime.now());
            log.warn("프로필 이미지 후처리 실패: taskId={}, type={}\n{}",
                    taskId, task.getTaskType(), ErrorLogSupport.stackTrace(failure));
            return;
        }
        taskRepository.delete(task);
    }

    private boolean shouldApply(ProfileImageTask task, User user) {
        String key = task.getImageKey();
        if (keyPolicy.isDefaultProfileImageKey(key)) {
            return false;
        }
        try {
            keyPolicy.validateProfileImageKey(user, key);
        } catch (ApiException invalidKey) {
            return false;
        }
        boolean currentImage = key.equals(user.getProfileImage());
        return task.getTaskType() == ProfileImageTask.Type.LINK ? currentImage : !currentImage;
    }
}
