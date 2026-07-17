package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;
import java.util.List;

public record AlternativeCommentsResponse(
        Long altSetId,
        List<Comment> comments,
        int page,
        int nextPage
) {
    public static AlternativeCommentsResponse from(Long altSetId, List<AlternativeComment> comments, int page, int nextPage) {
        return new AlternativeCommentsResponse(
                altSetId,
                comments.stream()
                    .map(Comment::from)
                    .toList(),
                page,
                nextPage
        );
    }

    public record Comment(
            Long commentId,
            String content,
            String username,
            LocalDateTime createdAt
    ) {
        public static Comment from(AlternativeComment comment) {
            return new Comment(
                    comment.getId(),
                    comment.getContent(),
                    comment.getUser().getNickname(),
                    comment.getCreatedAt()
            );
        }
    }
}
