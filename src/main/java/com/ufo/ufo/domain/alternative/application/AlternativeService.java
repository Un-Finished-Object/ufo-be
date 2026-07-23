package com.ufo.ufo.domain.alternative.application;

import com.ufo.ufo.domain.alternative.dao.AlternativeCommentRepository;
import com.ufo.ufo.domain.alternative.dao.AlternativeReactionRepository;
import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import com.ufo.ufo.domain.alternative.domain.AlternativeReaction;
import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentDeleteResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentUpdateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
import com.ufo.ufo.domain.alternative.exception.AlternativeCommentNotFoundException;
import com.ufo.ufo.domain.alternative.exception.AlternativeCommentPermissionDeniedException;
import com.ufo.ufo.domain.alternative.exception.AlternativeInteractionPermissionDeniedException;
import com.ufo.ufo.domain.alternative.exception.AlternativeNotFoundException;
import com.ufo.ufo.domain.pattern.dao.YarnAlternativeRepository;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlternativeService {

    private static final int COMMENTS_PAGE_SIZE = 5;

    private final UserService userService;
    private final YarnAlternativeRepository yarnAlternativeRepository;
    private final AlternativeReactionRepository alternativeReactionRepository;
    private final AlternativeCommentRepository alternativeCommentRepository;

    @Transactional
    public AlternativeReactionUpdateResponse updateReaction(User user, Long altId, UpdateAlternativeReactionRequest request) {
        validateInteractionPermission(user);
        AlternativeReactionType reactionType = AlternativeReactionType.from(request.type());
        YarnAlternative alternative = findAlternativeById(altId);
        User loginUser = userService.getUserById(user.getId());
        Optional<AlternativeReaction> existing = alternativeReactionRepository
                .findByYarnAlternative_IdAndUser_Id(altId, loginUser.getId());

        AlternativeReaction reaction = existing.orElseGet(() -> {
            AlternativeReaction createdReaction = AlternativeReaction.builder()
                    .yarnAlternative(alternative)
                    .user(loginUser)
                    .type(reactionType)
                    .build();
            alternativeReactionRepository.save(createdReaction);
            return createdReaction;
        });
        reaction.updateType(reactionType);

        long likesCount = alternativeReactionRepository
                .countByYarnAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        return AlternativeReactionUpdateResponse.from(
                altId,
                reactionType,
                likesCount,
                resolveReactionUpdatedAt(reaction)
        );
    }

    public AlternativeReactionResponse getReaction(User user, Long altId) {
        validateInteractionPermission(user);
        findAlternativeById(altId);
        Optional<AlternativeReaction> reaction = alternativeReactionRepository
                .findByYarnAlternative_IdAndUser_Id(altId, user.getId());
        int type = reaction
                .filter(existing -> existing.getType() == AlternativeReactionType.LIKE)
                .map(existing -> AlternativeReactionType.LIKE.code())
                .orElse(AlternativeReactionType.CANCEL.code());
        LocalDateTime updatedAt = reaction.map(this::resolveReactionUpdatedAt).orElse(null);
        long likesCount = alternativeReactionRepository
                .countByYarnAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        return AlternativeReactionResponse.from(altId, type, likesCount, updatedAt);
    }

    public AlternativeCommentsResponse getComments(User user, Long altId, Integer page) {
        validateInteractionPermission(user);
        findAlternativeById(altId);
        int pageNumber = normalizePage(page);
        Page<AlternativeComment> commentPage = alternativeCommentRepository.findAllByYarnAlternative_IdAndDeletedAtIsNull(
                        altId,
                        PageRequest.of(
                                pageNumber - 1,
                                COMMENTS_PAGE_SIZE,
                                Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))
                        )
                );
        int nextPage = resolveNextPage(pageNumber, commentPage.getTotalPages());
        return AlternativeCommentsResponse.from(altId, commentPage.getContent(), pageNumber, nextPage);
    }

    @Transactional
    public AlternativeCommentCreateResponse createComment(User user, Long altId, CreateAlternativeCommentRequest request) {
        validateInteractionPermission(user);
        YarnAlternative alternative = findAlternativeById(altId);
        User loginUser = userService.getUserById(user.getId());
        AlternativeComment comment = alternativeCommentRepository.save(AlternativeComment.builder()
                .yarnAlternative(alternative)
                .user(loginUser)
                .content(request.content())
                .build());
        return AlternativeCommentCreateResponse.from(altId, comment);
    }

    @Transactional
    public AlternativeCommentUpdateResponse updateComment(
            User user,
            Long altSetId,
            Long commentId,
            UpdateAlternativeCommentRequest request
    ) {
        validateInteractionPermission(user);
        findAlternativeById(altSetId);
        AlternativeComment comment = findActiveComment(altSetId, commentId);
        validateCommentOwner(user, comment);
        comment.updateContent(request.content());
        return AlternativeCommentUpdateResponse.from(altSetId, comment);
    }

    @Transactional
    public AlternativeCommentDeleteResponse deleteComment(User user, Long altSetId, Long commentId) {
        validateInteractionPermission(user);
        findAlternativeById(altSetId);
        AlternativeComment comment = findActiveComment(altSetId, commentId);
        validateCommentOwner(user, comment);
        comment.delete();
        return AlternativeCommentDeleteResponse.from(altSetId, comment);
    }

    private YarnAlternative findAlternativeById(Long altId) {
        return yarnAlternativeRepository.findById(altId)
                .orElseThrow(AlternativeNotFoundException::new);
    }

    private AlternativeComment findActiveComment(Long altSetId, Long commentId) {
        return alternativeCommentRepository.findByIdAndYarnAlternative_IdAndDeletedAtIsNull(commentId, altSetId)
                .orElseThrow(AlternativeCommentNotFoundException::new);
    }

    private void validateCommentOwner(User user, AlternativeComment comment) {
        if (!comment.isOwnedBy(user)) {
            throw new AlternativeCommentPermissionDeniedException();
        }
    }

    private void validateInteractionPermission(User user) {
        if (user.isGuest()) {
            throw new AlternativeInteractionPermissionDeniedException();
        }
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        if (remainingPages <= 0) {
            return 0;
        }
        return Math.min(remainingPages, 5);
    }

    private LocalDateTime resolveReactionUpdatedAt(AlternativeReaction reaction) {
        return reaction.getUpdatedAt() == null ? reaction.getCreatedAt() : reaction.getUpdatedAt();
    }
}
