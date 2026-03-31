package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse.UrlInfo;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import com.ufo.ufo.domain.image.exception.InvalidImageFileCountException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Value("${app.image.s3.bucket}")
    private String bucket;

    @Value("${app.image.s3.region}")
    private String region;

    @Value("${app.image.s3.url-expiration-minutes}")
    private long urlExpirationMinutes;

    @Value("${app.image.s3.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.image.max-bytes}")
    private long maxBytes;

    @Value("${app.image.max-file-count}")
    private int maxFileCount;

    @Value("${app.image.allowed-content-types}")
    private String allowedContentTypesValue;

    public ImagePresignedUrlIssueResponse issuePresignedUrls(ImagePresignedUrlIssueRequest request) {
        validateBucketConfigured();
        validateFileCount(request.fileCount());

        ImagePurpose purpose = ImagePurpose.from(request.purpose());
        Duration signatureDuration = Duration.ofMinutes(urlExpirationMinutes);
        Instant expiresAt = Instant.now().plus(signatureDuration);
        List<String> allowedContentTypes = resolveAllowedContentTypes();

        List<UrlInfo> urls = IntStream.range(0, request.fileCount())
                .mapToObj(index -> generateUrlInfo(purpose, signatureDuration))
                .toList();

        return ImagePresignedUrlIssueResponse.from(
                formatKst(expiresAt),
                maxBytes,
                allowedContentTypes,
                urls
        );
    }

    private UrlInfo generateUrlInfo(ImagePurpose purpose, Duration signatureDuration) {
        String key = generateObjectKey(purpose);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return UrlInfo.from(presignedRequest.url().toString(), buildImageUrl(key));
    }

    private String generateObjectKey(ImagePurpose purpose) {
        return purpose.prefix() + "/" + UUID.randomUUID();
    }

    private String buildImageUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl + key : publicBaseUrl + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private List<String> resolveAllowedContentTypes() {
        return Arrays.stream(allowedContentTypesValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private void validateBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new ImageBucketNotConfiguredException();
        }
    }

    private void validateFileCount(Integer fileCount) {
        if (fileCount == null || fileCount < 1 || fileCount > maxFileCount) {
            throw new InvalidImageFileCountException(maxFileCount);
        }
    }

    private String formatKst(Instant instant) {
        return instant.atZone(KST_ZONE_ID).format(KST_OFFSET_FORMATTER);
    }
}
