package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;

public record AlternativeCommentDeleteResponse(
        Long altSetId,
        Long commentId,
        LocalDateTime deletedAt
) {

    public static AlternativeCommentDeleteResponse from(Long altSetId, AlternativeComment comment) {
        return new AlternativeCommentDeleteResponse(
                altSetId,
                comment.getId(),
                comment.getDeletedAt()
        );
    }
}
