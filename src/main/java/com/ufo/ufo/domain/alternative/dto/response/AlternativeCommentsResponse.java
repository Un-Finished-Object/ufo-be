package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.time.LocalDateTime;
import java.util.List;

public record AlternativeCommentsResponse(
        Long altId,
        List<Comment> comments,
        int page
) {
    public static AlternativeCommentsResponse from(Long altId, List<AlternativeComment> comments, int page) {
        return new AlternativeCommentsResponse(
                altId,
                comments.stream()
                    .map(Comment::from)
                    .toList(),
                page
        );
    }

    public record Comment(
            String content,
            String username,
            LocalDateTime createdAt
    ) {
        public static Comment from(AlternativeComment comment) {
            return new Comment(
                    comment.getContent(),
                    comment.getUser().getNickname(),
                    comment.getCreatedAt()
            );
        }
    }
}
