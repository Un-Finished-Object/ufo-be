package com.ufo.ufo.domain.user.event;

public record ProfileImageChangedEvent(
        Long userId,
        String previousImageKey,
        String newImageKey
) {
}
