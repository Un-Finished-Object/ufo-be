package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest.FileInfo;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse.UrlInfo;
import com.ufo.ufo.domain.image.exception.ImageFileMetadataMismatchException;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import com.ufo.ufo.domain.image.exception.ImageDeletePermissionDeniedException;
import com.ufo.ufo.domain.image.exception.InvalidImageFileCountException;
import com.ufo.ufo.domain.image.exception.InvalidImageContentTypeException;
import com.ufo.ufo.domain.image.exception.InvalidImageUrlException;
import com.ufo.ufo.domain.image.exception.InvalidImageSizeException;
import com.ufo.ufo.domain.image.exception.InvalidProfileImageUrlException;
import com.ufo.ufo.domain.image.exception.ProfileImagePermissionDeniedException;
import com.ufo.ufo.domain.user.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageService {
    private static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final ImageProperties imageProperties;

    public ImagePresignedUrlIssueResponse issuePresignedUrls(User user, ImagePresignedUrlIssueRequest request) {
        validateBucketConfigured();
        validateFiles(request.fileCount(), request.files());

        ImagePurpose purpose = ImagePurpose.from(request.purpose());
        Duration signatureDuration = Duration.ofMinutes(imageProperties.s3().urlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(signatureDuration);
        List<String> allowedContentTypes = imageProperties.allowedContentTypes();

        List<UrlInfo> urls = request.files().stream()
                .map(file -> generateUrlInfo(user, purpose, signatureDuration, file))
                .toList();

        return ImagePresignedUrlIssueResponse.from(
                formatKst(expiresAt),
                imageProperties.maxBytes(),
                allowedContentTypes,
                urls
        );
    }

    public void deleteImage(User user, String imageUrl) {
        validateBucketConfigured();
        String key = extractObjectKey(imageUrl);
        validateDeleteOwnership(key, user.getId());
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(imageProperties.s3().bucket())
                .key(key)
                .build());
    }

    public void validateProfileImage(User user, String imageUrl) {
        validateBucketConfigured();
        if (hasQueryString(imageUrl)) {
            throw new InvalidProfileImageUrlException();
        }
        String key;
        try {
            key = extractObjectKey(imageUrl);
        } catch (InvalidImageUrlException e) {
            throw new InvalidProfileImageUrlException();
        }
        validatePrefix(key, ImagePurpose.PROFILE);
        validateProfileImageOwnership(key, user.getId());
    }

    private UrlInfo generateUrlInfo(User user, ImagePurpose purpose, Duration signatureDuration, FileInfo fileInfo) {
        String key = generateObjectKey(user.getId(), purpose);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(imageProperties.s3().bucket())
                .key(key)
                .contentType(fileInfo.contentType())
                .contentLength(fileInfo.contentLength())
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return UrlInfo.from(presignedRequest.url().toString(), buildImageUrl(key));
    }

    private String generateObjectKey(Long userId, ImagePurpose purpose) {
        return purpose.prefix() + "/" + userId + "/" + UUID.randomUUID();
    }

    private String buildImageUrl(String key) {
        String publicBaseUrl = imageProperties.s3().publicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl + key : publicBaseUrl + "/" + key;
        }
        return defaultS3BaseUrl() + "/" + key;
    }

    private void validateBucketConfigured() {
        String bucket = imageProperties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new ImageBucketNotConfiguredException();
        }
    }

    private void validateFiles(Integer fileCount, List<FileInfo> files) {
        validateFileMetadataCount(fileCount, files);
        validateFileCountRange(fileCount);
        validateFilePolicies(files);
    }

    private void validateFileMetadataCount(Integer fileCount, List<FileInfo> files) {
        if (fileCount == null || files == null || !fileCount.equals(files.size())) {
            throw new ImageFileMetadataMismatchException();
        }
    }

    private void validateFileCountRange(int fileCount) {
        int maxFileCount = imageProperties.maxFileCount();
        if (fileCount < 1 || fileCount > maxFileCount) {
            throw new InvalidImageFileCountException(maxFileCount);
        }
    }

    private void validateFilePolicies(List<FileInfo> files) {
        List<String> allowedContentTypes = imageProperties.allowedContentTypes();
        long maxBytes = imageProperties.maxBytes();

        files.forEach(file -> {
            validateContentType(file.contentType(), allowedContentTypes);
            validateContentLength(file.contentLength(), maxBytes);
        });
    }

    private void validateContentType(String contentType, List<String> allowedContentTypes) {
        if (!allowedContentTypes.contains(contentType)) {
            throw new InvalidImageContentTypeException(contentType, allowedContentTypes);
        }
    }

    private void validateContentLength(long contentLength, long maxBytes) {
        if (contentLength > maxBytes) {
            throw new InvalidImageSizeException(contentLength, maxBytes);
        }
    }

    private String formatKst(Instant instant) {
        return instant.atZone(KST_ZONE_ID).format(KST_OFFSET_FORMATTER);
    }

    private String extractObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new InvalidImageUrlException();
        }

        String urlWithoutQuery = imageUrl.split("\\?", 2)[0];
        String key = tryExtractKey(urlWithoutQuery, imageProperties.s3().publicBaseUrl());
        if (key == null) {
            key = tryExtractKey(urlWithoutQuery, defaultS3BaseUrl());
        }
        if (key == null || key.isBlank()) {
            throw new InvalidImageUrlException();
        }
        key = normalizeObjectKey(key);
        if (!isAllowedPrefix(key)) {
            throw new InvalidImageUrlException();
        }
        return key;
    }

    private String tryExtractKey(String imageUrl, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalizedBaseUrl = trimTrailingSlash(baseUrl);
        String expectedPrefix = normalizedBaseUrl + "/";
        if (!imageUrl.startsWith(expectedPrefix)) {
            return null;
        }
        return imageUrl.substring(expectedPrefix.length());
    }

    private boolean hasQueryString(String imageUrl) {
        return imageUrl != null && imageUrl.contains("?");
    }

    private String normalizeObjectKey(String key) {
        String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
        String[] segments = decodedKey.split("/");
        Deque<String> normalizedSegments = new ArrayDeque<>();

        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new InvalidImageUrlException();
            }
            normalizedSegments.addLast(segment);
        }

        return String.join("/", normalizedSegments);
    }

    private boolean isAllowedPrefix(String key) {
        return key.startsWith(ImagePurpose.STYLE.prefix() + "/")
                || key.startsWith(ImagePurpose.PROFILE.prefix() + "/")
                || key.startsWith(ImagePurpose.PATTERN.prefix() + "/");
    }

    private void validatePrefix(String key, ImagePurpose purpose) {
        if (!key.startsWith(purpose.prefix() + "/")) {
            throw new InvalidProfileImageUrlException();
        }
    }

    private void validateDeleteOwnership(String key, Long userId) {
        String[] parts = key.split("/", 3);
        if (parts.length < 3) {
            throw new InvalidImageUrlException();
        }
        if (userId == null || !parts[1].equals(String.valueOf(userId))) {
            throw new ImageDeletePermissionDeniedException();
        }
    }

    private void validateProfileImageOwnership(String key, Long userId) {
        String[] parts = key.split("/", 3);
        if (parts.length < 3) {
            throw new InvalidProfileImageUrlException();
        }
        if (userId == null || !parts[1].equals(String.valueOf(userId))) {
            throw new ProfileImagePermissionDeniedException();
        }
    }

    private String defaultS3BaseUrl() {
        return "https://" + imageProperties.s3().bucket() + ".s3." + imageProperties.s3().region() + ".amazonaws.com";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
