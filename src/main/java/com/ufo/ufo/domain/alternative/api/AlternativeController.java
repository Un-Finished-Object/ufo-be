package com.ufo.ufo.domain.alternative.api;

import com.ufo.ufo.domain.alternative.application.AlternativeService;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import com.ufo.ufo.global.validation.ValidPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/alternatives")
@RequiredArgsConstructor
@Validated
public class AlternativeController {

    private final AlternativeService alternativeService;

    @PutMapping("/{altId}/reaction")
    public ResponseEntity<ApiResponse<AlternativeReactionUpdateResponse>> updateReaction(
            @LoginUser User user,
            @PathVariable("altId") Long altId,
            @Valid @RequestBody UpdateAlternativeReactionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.updateReaction(user, altId, request)));
    }

    @GetMapping("/{altId}/reaction")
    public ResponseEntity<ApiResponse<AlternativeReactionResponse>> getReaction(
        @LoginUser User user,
        @PathVariable("altId") Long altId
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.getReaction(user, altId)));
    }

    @GetMapping("/{altId}/comments")
    public ResponseEntity<ApiResponse<AlternativeCommentsResponse>> getComments(
        @LoginUser User user,
        @PathVariable("altId") Long altId,
        @RequestParam("page")
        @ValidPage
        Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.getComments(user, altId, page)));
    }

    @PostMapping("/{altId}/comments")
    public ResponseEntity<ApiResponse<AlternativeCommentCreateResponse>> createComment(
            @LoginUser User user,
            @PathVariable("altId") Long altId,
            @Valid @RequestBody CreateAlternativeCommentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.createComment(user, altId, request)));
    }
}

