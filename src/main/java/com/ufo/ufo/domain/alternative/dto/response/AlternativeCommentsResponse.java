package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;

public record AlternativeCommentsResponse(
        Long altSetId,
        List<Comment> comments,
        int page,
        int nextPage
) {
    public static AlternativeCommentsResponse from(Long altSetId, List<AlternativeComment> comments, int page, int nextPage, User loginUser) {
        return new AlternativeCommentsResponse(
                altSetId,
                comments.stream()
                    .map(comment -> Comment.from(comment, loginUser))
                    .toList(),
                page,
                nextPage
        );
    }

    public record Comment(
            Long commentId,
            String content,
            String username,
            Boolean isMine,
            LocalDateTime createdAt
    ) {
        public static Comment from(AlternativeComment comment, User loginUser) {
            return new Comment(
                    comment.getId(),
                    comment.getContent(),
                    comment.getUser().getNickname(),
                    comment.isOwnedBy(loginUser),
                    comment.getCreatedAt()
            );
        }
    }
}
