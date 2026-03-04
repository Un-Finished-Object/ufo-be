package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;

public record AlternativeCommentCreateResponse(
        Long altId,
        String content,
        String username,
        LocalDateTime createdAt
) {
    public static AlternativeCommentCreateResponse from(Long altId, AlternativeComment comment) {
        return new AlternativeCommentCreateResponse(
                altId,
                comment.getContent(),
                comment.getUser().getNickname(),
                comment.getCreatedAt()
        );
    }
}
