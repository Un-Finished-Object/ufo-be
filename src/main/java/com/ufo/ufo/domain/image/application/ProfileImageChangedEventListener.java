package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import com.ufo.ufo.domain.user.event.ProfileImageChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProfileImageChangedEventListener {

    private final ProfileImageTaskRepository taskRepository;
    private final ImageKeyPolicy keyPolicy;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordTasks(ProfileImageChangedEvent event) {
        if (!keyPolicy.isDefaultProfileImageKey(event.newImageKey())) {
            taskRepository.save(new ProfileImageTask(event.userId(), ProfileImageTask.Type.LINK, event.newImageKey()));
        }
        if (keyPolicy.shouldDeletePreviousProfileImage(event.previousImageKey(), event.newImageKey())) {
            taskRepository.save(new ProfileImageTask(
                    event.userId(), ProfileImageTask.Type.DELETE, event.previousImageKey()));
        }
    }
}
