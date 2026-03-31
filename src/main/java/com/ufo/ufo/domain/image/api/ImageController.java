package com.ufo.ufo.domain.image.api;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.image.dto.request.ImagePresignedUrlIssueRequest;
import com.ufo.ufo.domain.image.dto.response.ImagePresignedUrlIssueResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/images")
@RequiredArgsConstructor
@Validated
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/presigned-urls")
    public ResponseEntity<ApiResponse<ImagePresignedUrlIssueResponse>> issuePresignedUrls(
            @LoginUser User user,
            @Valid @RequestBody ImagePresignedUrlIssueRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(imageService.issuePresignedUrls(user, request)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteImage(
            @LoginUser User user,
            @RequestParam("imageUrl")
            @NotBlank(message = "imageUrl은 필수입니다.")
            String imageUrl
    ) {
        imageService.deleteImage(user, imageUrl);
        return ResponseEntity.noContent().build();
    }
}
