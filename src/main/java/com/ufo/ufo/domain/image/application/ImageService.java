package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse.UrlInfo;
import com.ufo.ufo.domain.user.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final ImageProperties imageProperties;
    private final ImageUploadValidator uploadValidator;
    private final ImageKeyPolicy keyPolicy;
    private final S3PostPolicySigner postPolicySigner;
    private final S3ImageObjectManager objectManager;

    public ImagePresignedUrlIssueResponse issuePresignedUrls(User user, ImagePresignedUrlIssueRequest request) {
        ImagePurpose purpose = uploadValidator.validate(request);
        Duration signatureDuration = Duration.ofMinutes(imageProperties.s3().urlExpirationMinutes());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(signatureDuration);
        Long ownerId = user.getId();

        List<UrlInfo> urls = request.files().stream()
                .map(file -> {
                    String key = keyPolicy.generateObjectKey(ownerId, purpose);
                    S3PostPolicySigner.PostForm form = postPolicySigner.createForm(
                            key, file.contentType(), now, signatureDuration
                    );
                    return UrlInfo.from(
                            form.endpointUrl(), key, keyPolicy.buildImageUrl(key), form.uploadFields()
                    );
                })
                .toList();

        return ImagePresignedUrlIssueResponse.from(
                expiresAt.atZone(KST_ZONE_ID).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                imageProperties.maxBytes(),
                imageProperties.allowedContentTypes(),
                urls
        );
    }

    public String buildImageUrl(String key) {
        return keyPolicy.buildImageUrl(key);
    }

    public void validateProfileImageKey(User user, String imageKey) {
        keyPolicy.validateProfileImageKey(user, imageKey);
    }

    public void completeProfileImageReplacement(String newImageKey, String previousImageKey) {
        String normalizedNewImageKey = keyPolicy.normalizeImageKey(newImageKey);
        if (!keyPolicy.isDefaultProfileImageKey(normalizedNewImageKey)) {
            keyPolicy.validateProfilePrefix(normalizedNewImageKey);
            objectManager.markLinked(normalizedNewImageKey);
        }

        if (keyPolicy.shouldDeletePreviousProfileImage(previousImageKey, normalizedNewImageKey)) {
            String previousKey = keyPolicy.normalizeImageKey(previousImageKey);
            keyPolicy.validateProfilePrefix(previousKey);
            objectManager.delete(previousKey);
        }
    }
}
