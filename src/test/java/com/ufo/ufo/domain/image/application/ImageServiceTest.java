package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.exception.ImageBucketNotConfiguredException;
import com.ufo.ufo.domain.image.exception.ImageDeletePermissionDeniedException;
import com.ufo.ufo.domain.image.exception.InvalidImageFileCountException;
import com.ufo.ufo.domain.image.exception.InvalidImagePurposeException;
import com.ufo.ufo.domain.image.exception.InvalidImageUrlException;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("이미지 서비스 테스트")
class ImageServiceTest {

    private static final ImageProperties IMAGE_PROPERTIES = new ImageProperties(
            5,
            10_485_760L,
            List.of("image/jpeg", "image/png", "image/webp"),
            new ImageProperties.S3(
                    "ufo-bucket",
                    "ap-northeast-2",
                    5L,
                    "https://cdn.ufo.com"
            )
    );

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private ImageService imageService;
    private User user;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(s3Presigner, s3Client, IMAGE_PROPERTIES);
        user = UserFixture.createUserWithId(1L);
    }

    @Test
    @DisplayName("Presigned URL 발급은 요청 개수만큼 URL 목록과 정책 정보를 반환해야 한다")
    void issuePresignedUrls_ReturnsUrlsAndPolicy() throws Exception {
        PresignedPutObjectRequest first = mockPresignedRequest("https://s3.example.com/presigned-1");
        PresignedPutObjectRequest second = mockPresignedRequest("https://s3.example.com/presigned-2");
        when(s3Presigner.presignPutObject(org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)))
                .thenReturn(first)
                .thenReturn(second);

        ImagePresignedUrlIssueResponse response = imageService.issuePresignedUrls(
                user,
                new ImagePresignedUrlIssueRequest(2, "STYLE")
        );

        assertThat(response.allowedContentTypes()).containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(response.maxBytes()).isEqualTo(10_485_760L);
        assertThat(response.expiresAt()).contains("+09:00");
        assertThat(response.urls()).hasSize(2);
        assertThat(response.urls().getFirst().presignedUrl()).isEqualTo("https://s3.example.com/presigned-1");
        assertThat(response.urls().getFirst().imageUrl()).startsWith("https://cdn.ufo.com/styles/1/");
        verify(s3Presigner, times(2)).presignPutObject(org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignPutObject(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(req -> {
                    assertThat(req.signatureDuration().toMinutes()).isEqualTo(5L);
                    assertThat(req.putObjectRequest().bucket()).isEqualTo("ufo-bucket");
                    assertThat(req.putObjectRequest().key()).startsWith("styles/1/");
                });
    }

    @Test
    @DisplayName("fileCount가 정책 범위를 벗어나면 예외가 발생해야 한다")
    void issuePresignedUrls_InvalidFileCount_Throws() {
        assertThatThrownBy(() -> imageService.issuePresignedUrls(user, new ImagePresignedUrlIssueRequest(6, "STYLE")))
                .isInstanceOf(InvalidImageFileCountException.class);
    }

    @Test
    @DisplayName("purpose가 허용값이 아니면 예외가 발생해야 한다")
    void issuePresignedUrls_InvalidPurpose_Throws() {
        assertThatThrownBy(() -> imageService.issuePresignedUrls(user, new ImagePresignedUrlIssueRequest(1, "UNKNOWN")))
                .isInstanceOf(InvalidImagePurposeException.class);
    }

    @Test
    @DisplayName("버킷 설정이 비어 있으면 예외가 발생해야 한다")
    void issuePresignedUrls_EmptyBucket_Throws() {
        ImageProperties emptyBucketProperties = new ImageProperties(
                5,
                10_485_760L,
                List.of("image/jpeg", "image/png", "image/webp"),
                new ImageProperties.S3("", "ap-northeast-2", 5L, "https://cdn.ufo.com")
        );
        ImageService imageService = new ImageService(s3Presigner, s3Client, emptyBucketProperties);

        assertThatThrownBy(() -> imageService.issuePresignedUrls(user, new ImagePresignedUrlIssueRequest(1, "STYLE")))
                .isInstanceOf(ImageBucketNotConfiguredException.class);
    }

    @Test
    @DisplayName("이미지 삭제는 유효한 내부 URL에서 key를 추출해 S3 deleteObject를 호출해야 한다")
    void deleteImage_ValidUrl_DeletesObject() {
        String imageUrl = "https://cdn.ufo.com/styles/1/123e4567-e89b-12d3-a456-426614174000";

        imageService.deleteImage(user, imageUrl);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("ufo-bucket");
        assertThat(captor.getValue().key()).isEqualTo("styles/1/123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    @DisplayName("이미지 삭제는 외부 URL이면 예외가 발생하고 삭제를 호출하지 않아야 한다")
    void deleteImage_ExternalUrl_Throws() {
        assertThatThrownBy(() -> imageService.deleteImage(user, "https://evil.com/styles/123"))
                .isInstanceOf(InvalidImageUrlException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("이미지 삭제는 허용되지 않은 prefix면 예외가 발생해야 한다")
    void deleteImage_DisallowedPrefix_Throws() {
        assertThatThrownBy(() -> imageService.deleteImage(user, "https://cdn.ufo.com/unknown/123"))
                .isInstanceOf(InvalidImageUrlException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("이미지 삭제는 본인 소유가 아니면 예외가 발생해야 한다")
    void deleteImage_NotOwner_Throws() {
        assertThatThrownBy(() -> imageService.deleteImage(user, "https://cdn.ufo.com/styles/2/123"))
                .isInstanceOf(ImageDeletePermissionDeniedException.class);
        verifyNoInteractions(s3Client);
    }

    private PresignedPutObjectRequest mockPresignedRequest(String url) throws MalformedURLException {
        PresignedPutObjectRequest request = mock(PresignedPutObjectRequest.class);
        when(request.url()).thenReturn(new URL(url));
        return request;
    }
}
