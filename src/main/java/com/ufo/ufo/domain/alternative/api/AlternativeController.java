package com.ufo.ufo.domain.alternative.api;

import com.ufo.ufo.domain.alternative.application.AlternativeService;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentDeleteResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentUpdateResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PutMapping("/{altSetId}/reaction")
    public ResponseEntity<ApiResponse<AlternativeReactionUpdateResponse>> updateReaction(
            @LoginUser User user,
            @PathVariable("altSetId") Long altSetId,
            @Valid @RequestBody UpdateAlternativeReactionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.updateReaction(user, altSetId, request)));
    }

    @GetMapping("/{altSetId}/reaction")
    public ResponseEntity<ApiResponse<AlternativeReactionResponse>> getReaction(
        @LoginUser User user,
        @PathVariable("altSetId") Long altSetId
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.getReaction(user, altSetId)));
    }

    @GetMapping("/{altSetId}/comments")
    public ResponseEntity<ApiResponse<AlternativeCommentsResponse>> getComments(
        @LoginUser User user,
        @PathVariable("altSetId") Long altSetId,
        @RequestParam("page")
        @ValidPage
        Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.getComments(user, altSetId, page)));
    }

    @PostMapping("/{altSetId}/comments")
    public ResponseEntity<ApiResponse<AlternativeCommentCreateResponse>> createComment(
            @LoginUser User user,
            @PathVariable("altSetId") Long altSetId,
            @Valid @RequestBody CreateAlternativeCommentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(alternativeService.createComment(user, altSetId, request)));
    }

    @PatchMapping("/{altSetId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<AlternativeCommentUpdateResponse>> updateComment(
            @LoginUser User user,
            @PathVariable("altSetId") Long altSetId,
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody UpdateAlternativeCommentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                alternativeService.updateComment(user, altSetId, commentId, request)
        ));
    }

    @DeleteMapping("/{altSetId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<AlternativeCommentDeleteResponse>> deleteComment(
            @LoginUser User user,
            @PathVariable("altSetId") Long altSetId,
            @PathVariable("commentId") Long commentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                alternativeService.deleteComment(user, altSetId, commentId)
        ));
    }
}

