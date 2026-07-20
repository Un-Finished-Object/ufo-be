package com.ufo.ufo.domain.user.event;

public record ProfileImageChangedEvent(
        String previousImageKey,
        String newImageKey
) {
}
