package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

@Component
@RequiredArgsConstructor
public class S3ImageObjectManager {

    private static final String UPLOAD_STATUS_TAG_KEY = "ufo-upload-status";
    private static final String UPLOAD_STATUS_LINKED = "linked";

    private final S3Client s3Client;
    private final ImageProperties imageProperties;

    void markLinked(String key) {
        String bucket = imageProperties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new ImageBucketNotConfiguredException();
        }
        s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                .bucket(bucket)
                .key(key)
                .tagging(Tagging.builder()
                        .tagSet(Tag.builder()
                                .key(UPLOAD_STATUS_TAG_KEY)
                                .value(UPLOAD_STATUS_LINKED)
                                .build())
                        .build())
                .build());
    }

    void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(imageProperties.s3().bucket())
                .key(key)
                .build());
    }
}
