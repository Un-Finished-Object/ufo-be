package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest.FileInfo;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import com.ufo.ufo.domain.image.exception.ImageFileMetadataMismatchException;
import com.ufo.ufo.domain.image.exception.InvalidImageContentTypeException;
import com.ufo.ufo.domain.image.exception.InvalidImageFileCountException;
import com.ufo.ufo.domain.image.exception.InvalidImagePurposeException;
import com.ufo.ufo.domain.image.exception.InvalidImageSizeException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUploadValidator {

    private final ImageProperties imageProperties;

    public ImagePurpose validate(ImagePresignedUrlIssueRequest request) {
        validateBucketConfigured();
        validateFiles(request.fileCount(), request.files());
        ImagePurpose purpose = ImagePurpose.from(request.purpose());
        if (purpose != ImagePurpose.PROFILE) {
            throw new InvalidImagePurposeException();
        }
        return purpose;
    }

    void validateBucketConfigured() {
        String bucket = imageProperties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new ImageBucketNotConfiguredException();
        }
    }

    private void validateFiles(Integer fileCount, List<FileInfo> files) {
        if (fileCount == null || files == null || !fileCount.equals(files.size())) {
            throw new ImageFileMetadataMismatchException();
        }

        int maxFileCount = imageProperties.maxFileCount();
        if (fileCount < 1 || fileCount > maxFileCount) {
            throw new InvalidImageFileCountException(maxFileCount);
        }

        List<String> allowedContentTypes = imageProperties.allowedContentTypes();
        long maxBytes = imageProperties.maxBytes();
        files.forEach(file -> {
            if (!allowedContentTypes.contains(file.contentType())) {
                throw new InvalidImageContentTypeException(file.contentType(), allowedContentTypes);
            }
            if (file.contentLength() > maxBytes) {
                throw new InvalidImageSizeException(file.contentLength(), maxBytes);
            }
        });
    }
}
