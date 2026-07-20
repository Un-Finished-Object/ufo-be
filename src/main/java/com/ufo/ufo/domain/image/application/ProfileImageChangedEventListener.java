package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.user.event.ProfileImageChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProfileImageChangedEventListener {

    private final ImageService imageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProfileImageChangedEvent event) {
        imageService.completeProfileImageReplacement(event.newImageKey(), event.previousImageKey());
    }
}
