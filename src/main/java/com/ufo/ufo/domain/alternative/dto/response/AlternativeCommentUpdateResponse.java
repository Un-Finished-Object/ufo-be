package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;

public record AlternativeCommentUpdateResponse(
        Long altSetId,
        Long commentId,
        String content,
        String username,
        LocalDateTime updatedAt
) {

    public static AlternativeCommentUpdateResponse from(Long altSetId, AlternativeComment comment) {
        return new AlternativeCommentUpdateResponse(
                altSetId,
                comment.getId(),
                comment.getContent(),
                comment.getUser().getNickname(),
                comment.getUpdatedAt()
        );
    }
}
