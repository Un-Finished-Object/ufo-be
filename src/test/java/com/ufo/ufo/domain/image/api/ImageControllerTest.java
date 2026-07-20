package com.ufo.ufo.domain.image.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest.FileInfo;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse.UrlInfo;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("이미지 컨트롤러 테스트")
class ImageControllerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageController imageController;

    @Test
    @DisplayName("Presigned URL 발급 API는 서비스 응답을 data에 담아 반환해야 한다")
    void issuePresignedUrls_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        ImagePresignedUrlIssueRequest request = new ImagePresignedUrlIssueRequest(
                2,
                "PROFILE",
                null,
                List.of(new FileInfo("image/jpeg", 1_024L), new FileInfo("image/png", 2_048L))
        );
        ImagePresignedUrlIssueResponse serviceResponse = ImagePresignedUrlIssueResponse.from(
                "2026-03-31T18:00:00+09:00",
                10_485_760L,
                List.of("image/jpeg", "image/png", "image/webp"),
                List.of(UrlInfo.from(
                        "https://example.com/presigned",
                        "profiles/1/image",
                        "https://example.com/image",
                        Map.of("x-amz-tagging", "ufo-upload-status=issued")
                ))
        );
        when(imageService.issuePresignedUrls(user, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<ImagePresignedUrlIssueResponse>> response =
                imageController.issuePresignedUrls(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().expiresAt()).isEqualTo("2026-03-31T18:00:00+09:00");
        assertThat(response.getBody().data().urls()).hasSize(1);
        assertThat(response.getBody().error()).isNull();
        verify(imageService).issuePresignedUrls(user, request);
    }

}
