package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest.FileInfo;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse.UrlInfo;
import com.ufo.ufo.domain.image.exception.ImageCdnBaseUrlNotConfiguredException;
import com.ufo.ufo.domain.image.exception.ImageFileMetadataMismatchException;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import com.ufo.ufo.domain.image.exception.InvalidImageFileCountException;
import com.ufo.ufo.domain.image.exception.InvalidImageContentTypeException;
import com.ufo.ufo.domain.image.exception.InvalidImageKeyException;
import com.ufo.ufo.domain.image.exception.InvalidImagePurposeException;
import com.ufo.ufo.domain.image.exception.InvalidImageSizeException;
import com.ufo.ufo.domain.image.exception.InvalidProfileImageUrlException;
import com.ufo.ufo.domain.image.exception.ProfileImagePermissionDeniedException;
import com.ufo.ufo.domain.user.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

@Service
@RequiredArgsConstructor
public class ImageService {
    private static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter UTC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter UTC_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO_EXPIRATION_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final String UPLOAD_STATUS_TAG_KEY = "ufo-upload-status";
    private static final String UPLOAD_STATUS_ISSUED = "issued";
    private static final String UPLOAD_STATUS_LINKED = "linked";
    private static final String S3_TAGGING_HEADER = "x-amz-tagging";
    private static final String AWS_ALGORITHM = "AWS4-HMAC-SHA256";

    private final S3Client s3Client;
    private final AwsCredentialsProvider credentialsProvider;
    private final ImageProperties imageProperties;
    private final ObjectMapper objectMapper;

    public ImagePresignedUrlIssueResponse issuePresignedUrls(User user, ImagePresignedUrlIssueRequest request) {
        validateBucketConfigured();
        validateFiles(request.fileCount(), request.files());

        ImagePurpose purpose = ImagePurpose.from(request.purpose());
        validateIssuePurpose(purpose);
        Duration signatureDuration = Duration.ofMinutes(imageProperties.s3().urlExpirationMinutes());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(signatureDuration);
        List<String> allowedContentTypes = imageProperties.allowedContentTypes();
        Long ownerId = user.getId();

        List<UrlInfo> urls = request.files().stream()
                .map(file -> generateUrlInfo(ownerId, purpose, now, signatureDuration, file))
                .toList();

        return ImagePresignedUrlIssueResponse.from(
                formatKst(expiresAt),
                imageProperties.maxBytes(),
                allowedContentTypes,
                urls
        );
    }

    private UrlInfo generateUrlInfo(Long ownerId, ImagePurpose purpose, Instant now, Duration signatureDuration, FileInfo fileInfo) {
        String key = generateObjectKey(ownerId, purpose);
        String contentType = fileInfo.contentType();

        String base64Policy = createBase64Policy(key, contentType, now, signatureDuration);
        String signature = calculateSignature(base64Policy, now);
        Map<String, String> uploadFields = buildUploadFields(key, contentType, now, base64Policy, signature);

        return UrlInfo.from(
                buildS3EndpointUrl(),
                key,
                buildImageUrl(key),
                uploadFields
        );
    }

    private String createBase64Policy(String key, String contentType, Instant now, Duration signatureDuration) {
        String bucket = imageProperties.s3().bucket();
        String region = imageProperties.s3().region();
        long maxBytes = imageProperties.maxBytes();

        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        String credentialStr = buildCredentialString(credentials.accessKeyId(), formatUtcDate(now), region);
        String expirationStr = formatIsoExpiration(now.plus(signatureDuration));
        String taggingValue = issuedUploadTaggingHeaderValue();
        String sessionToken = (credentials instanceof AwsSessionCredentials sessionCredentials) ? sessionCredentials.sessionToken() : null;

        Map<String, Object> policyMap = Map.of(
                "expiration", expirationStr,
                "conditions", buildPolicyConditions(bucket, key, contentType, taggingValue, credentialStr, formatUtcDateTime(now), sessionToken, maxBytes)
        );

        return Base64.getEncoder().encodeToString(serializeJson(policyMap).getBytes(StandardCharsets.UTF_8));
    }

    private List<Object> buildPolicyConditions(String bucket, String key, String contentType, String taggingValue, String credentialStr, String dateTimeStr, String sessionToken, long maxBytes) {
        return Stream.of(
                condition("bucket", bucket),
                condition("key", key),
                condition("Content-Type", contentType),
                condition(S3_TAGGING_HEADER, taggingValue),
                rangeCondition("content-length-range", 1, maxBytes),
                condition("x-amz-algorithm", AWS_ALGORITHM),
                condition("x-amz-credential", credentialStr),
                condition("x-amz-date", dateTimeStr),
                isNotEmpty(sessionToken) ? condition("x-amz-security-token", sessionToken) : null
        )
        .filter(Objects::nonNull)
        .toList();
    }

    private Map<String, String> condition(String key, String value) {
        return Map.of(key, value);
    }

    private List<Object> rangeCondition(String name, long min, long max) {
        return List.of(name, min, max);
    }

    private String calculateSignature(String base64Policy, Instant now) {
        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        String dateStr = formatUtcDate(now);
        String region = imageProperties.s3().region();

        byte[] kDate = hmacSha256(("AWS4" + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8), dateStr);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, "s3");
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        byte[] signatureBytes = hmacSha256(kSigning, base64Policy);

        return bytesToHex(signatureBytes);
    }

    private Map<String, String> buildUploadFields(String key, String contentType, Instant now, String base64Policy, String signature) {
        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        String region = imageProperties.s3().region();
        String dateStr = formatUtcDate(now);
        String dateTimeStr = formatUtcDateTime(now);
        String credentialStr = buildCredentialString(credentials.accessKeyId(), dateStr, region);

        Map<String, String> uploadFields = new LinkedHashMap<>();
        uploadFields.put("key", key);
        uploadFields.put(HttpHeaders.CONTENT_TYPE, contentType);
        uploadFields.put(S3_TAGGING_HEADER, issuedUploadTaggingHeaderValue());
        uploadFields.put("x-amz-algorithm", AWS_ALGORITHM);
        uploadFields.put("x-amz-credential", credentialStr);
        uploadFields.put("x-amz-date", dateTimeStr);

        if (credentials instanceof AwsSessionCredentials sessionCredentials && isNotEmpty(sessionCredentials.sessionToken())) {
            uploadFields.put("x-amz-security-token", sessionCredentials.sessionToken());
        }

        uploadFields.put("policy", base64Policy);
        uploadFields.put("x-amz-signature", signature);
        return uploadFields;
    }

    private String buildCredentialString(String accessKeyId, String dateStr, String region) {
        return accessKeyId + "/" + dateStr + "/" + region + "/s3/aws4_request";
    }

    private String serializeJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.isBlank();
    }

    private String formatUtcDate(Instant instant) {
        return UTC_DATE_FORMATTER.format(instant);
    }

    private String formatUtcDateTime(Instant instant) {
        return UTC_DATETIME_FORMATTER.format(instant);
    }

    private String formatIsoExpiration(Instant instant) {
        return ISO_EXPIRATION_FORMATTER.format(instant);
    }

    private String buildS3EndpointUrl() {
        String publicBaseUrl = imageProperties.s3().publicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl;
        }
        return "https://" + imageProperties.s3().bucket() + ".s3." + imageProperties.s3().region() + ".amazonaws.com";
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String generateObjectKey(Long ownerId, ImagePurpose purpose) {
        return purpose.prefix() + "/" + ownerId + "/" + UUID.randomUUID();
    }

    public String buildImageUrl(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String cdnBaseUrl = imageProperties.cdnBaseUrl();
        if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
            return joinBaseUrlAndKey(cdnBaseUrl, encodeObjectKey(key));
        }
        throw new ImageCdnBaseUrlNotConfiguredException();
    }

    private String encodeObjectKey(String key) {
        return Arrays.stream(key.split("/", -1))
                .map(this::encodePathSegment)
                .collect(Collectors.joining("/"));
    }

    private String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public void validateProfileImageKey(User user, String imageKey) {
        String key;
        try {
            key = normalizeImageKey(imageKey);
            if (isDefaultProfileImageKey(key)) {
                return;
            }
            validatePrefix(key, ImagePurpose.PROFILE);
        } catch (InvalidImageKeyException e) {
            throw new InvalidProfileImageUrlException();
        }
        validateProfileImageOwnership(key, user.getId());
    }

    public void completeProfileImageReplacement(String newImageKey, String previousImageKey) {
        String normalizedNewImageKey = normalizeImageKey(newImageKey);
        if (!isDefaultProfileImageKey(normalizedNewImageKey)) {
            validatePrefix(normalizedNewImageKey, ImagePurpose.PROFILE);
            markImageLinked(normalizedNewImageKey);
        }

        if (shouldDeletePreviousProfileImage(previousImageKey, normalizedNewImageKey)) {
            deleteObject(normalizeImageKey(previousImageKey));
        }
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

    private void validateIssuePurpose(ImagePurpose purpose) {
        if (purpose != ImagePurpose.PROFILE) {
            throw new InvalidImagePurposeException();
        }
    }

    private Map<String, String> buildUploadHeaders(String contentType) {
        return Map.of(
                HttpHeaders.CONTENT_TYPE, contentType,
                S3_TAGGING_HEADER, issuedUploadTaggingHeaderValue()
        );
    }

    private String formatKst(Instant instant) {
        return instant.atZone(KST_ZONE_ID).format(KST_OFFSET_FORMATTER);
    }

    private String normalizeImageKey(String key) {
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

    private void validatePrefix(String key, ImagePurpose purpose) {
        if (!key.startsWith(purpose.prefix() + "/")) {
            throw new InvalidImageKeyException();
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

    private void markImageLinked(String key) {
        validateBucketConfigured();
        s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                .bucket(imageProperties.s3().bucket())
                .key(key)
                .tagging(Tagging.builder()
                        .tagSet(Tag.builder()
                                .key(UPLOAD_STATUS_TAG_KEY)
                                .value(UPLOAD_STATUS_LINKED)
                                .build())
                        .build())
                .build());
    }

    private boolean isDefaultProfileImageKey(String key) {
        return key != null && key.equals(imageProperties.defaultProfileImageKey());
    }

    private boolean shouldDeletePreviousProfileImage(String previousImageKey, String newImageKey) {
        return previousImageKey != null
                && !previousImageKey.isBlank()
                && !previousImageKey.equals(newImageKey)
                && !isDefaultProfileImageKey(previousImageKey);
    }

    private void deleteObject(String key) {
        if (isDefaultProfileImageKey(key)) {
            return;
        }
        validatePrefix(key, ImagePurpose.PROFILE);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(imageProperties.s3().bucket())
                .key(key)
                .build());
    }

    private String issuedUploadTaggingHeaderValue() {
        return UPLOAD_STATUS_TAG_KEY + "=" + UPLOAD_STATUS_ISSUED;
    }

    private String joinBaseUrlAndKey(String baseUrl, String key) {
        return baseUrl.endsWith("/") ? baseUrl + key : baseUrl + "/" + key;
    }
}
