package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.exception.ImageCdnBaseUrlNotConfiguredException;
import com.ufo.ufo.domain.image.exception.InvalidImageKeyException;
import com.ufo.ufo.domain.image.exception.InvalidProfileImageUrlException;
import com.ufo.ufo.domain.image.exception.ProfileImagePermissionDeniedException;
import com.ufo.ufo.domain.user.domain.User;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageKeyPolicy {

    private final ImageProperties imageProperties;

    String generateObjectKey(Long ownerId, ImagePurpose purpose) {
        return purpose.prefix() + "/" + ownerId + "/" + UUID.randomUUID();
    }

    String buildImageUrl(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String cdnBaseUrl = imageProperties.cdnBaseUrl();
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new ImageCdnBaseUrlNotConfiguredException();
        }
        String encodedKey = Arrays.stream(key.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        return cdnBaseUrl.endsWith("/") ? cdnBaseUrl + encodedKey : cdnBaseUrl + "/" + encodedKey;
    }

    void validateProfileImageKey(User user, String imageKey) {
        String key;
        try {
            key = normalizeImageKey(imageKey);
            if (isDefaultProfileImageKey(key)) {
                return;
            }
            validateProfilePrefix(key);
        } catch (InvalidImageKeyException e) {
            throw new InvalidProfileImageUrlException();
        }

        String[] parts = key.split("/", 3);
        if (parts.length < 3) {
            throw new InvalidProfileImageUrlException();
        }
        if (user.getId() == null || !parts[1].equals(String.valueOf(user.getId()))) {
            throw new ProfileImagePermissionDeniedException();
        }
    }

    String normalizeImageKey(String key) {
        if (key == null || key.isBlank() || key.contains("://") || key.contains("?") || key.contains("%")) {
            throw new InvalidImageKeyException();
        }

        String[] segments = key.split("/", -1);
        Deque<String> normalizedSegments = new ArrayDeque<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new InvalidImageKeyException();
            }
            normalizedSegments.addLast(segment);
        }
        return String.join("/", normalizedSegments);
    }

    void validateProfilePrefix(String key) {
        if (!key.startsWith(ImagePurpose.PROFILE.prefix() + "/")) {
            throw new InvalidImageKeyException();
        }
    }

    boolean isDefaultProfileImageKey(String key) {
        return key != null && key.equals(imageProperties.defaultProfileImageKey());
    }

    boolean shouldDeletePreviousProfileImage(String previousImageKey, String newImageKey) {
        return previousImageKey != null
                && !previousImageKey.isBlank()
                && !previousImageKey.equals(newImageKey)
                && !isDefaultProfileImageKey(previousImageKey);
    }
}
