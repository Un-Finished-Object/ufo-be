package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;

public record AlternativeCommentResponse(
        Long commentId,
        Long userId,
        String nickname,
        String content,
        LocalDateTime createdAt
) {
    public static AlternativeCommentResponse from(AlternativeComment comment) {
        return new AlternativeCommentResponse(
                comment.getId(),
                comment.getUser().getId(),
                comment.getUser().getNickname(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
