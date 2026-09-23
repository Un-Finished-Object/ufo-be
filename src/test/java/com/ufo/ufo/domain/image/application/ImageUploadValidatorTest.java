package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.domain.ImagePurpose;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest.FileInfo;
import com.ufo.ufo.domain.image.exception.InvalidImageContentTypeException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageUploadValidatorTest {

    private final ImageUploadValidator validator = new ImageUploadValidator(new ImageProperties(
            5, 10_485_760L, List.of("image/jpeg"), "https://cdn.example.com", "defaults/profile.png",
            new ImageProperties.S3("bucket", "ap-northeast-2", 5L, "https://s3.example.com")
    ));

    @Test
    void acceptsProfileImageRequest() {
        var request = new ImagePresignedUrlIssueRequest(
                1, "PROFILE", null, List.of(new FileInfo("image/jpeg", 1024L))
        );

        assertThat(validator.validate(request)).isEqualTo(ImagePurpose.PROFILE);
    }

    @Test
    void rejectsUnsupportedContentType() {
        var request = new ImagePresignedUrlIssueRequest(
                1, "PROFILE", null, List.of(new FileInfo("application/pdf", 1024L))
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidImageContentTypeException.class);
    }
}
