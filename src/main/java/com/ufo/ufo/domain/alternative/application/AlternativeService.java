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
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
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
    private final CreditService creditService;
    private final PatternAlternativeYarnRepository patternAlternativeYarnRepository;
    private final AlternativeReactionRepository alternativeReactionRepository;
    private final AlternativeCommentRepository alternativeCommentRepository;

    @Transactional
    public AlternativeReactionUpdateResponse updateReaction(User user, Long altId, UpdateAlternativeReactionRequest request) {
        validateInteractionPermission(user);
        AlternativeReactionType reactionType = AlternativeReactionType.from(request.type());
        PatternAlternativeYarn alternative = findAlternativeById(altId);
        User loginUser = userService.getUserById(user.getId());
        Optional<AlternativeReaction> existing = alternativeReactionRepository.findByAlternative_IdAndUser_Id(altId, loginUser.getId());

        LocalDateTime updatedAt = LocalDateTime.now();
        if (reactionType.isCancel()) {
            existing.ifPresent(alternativeReactionRepository::delete);
        } else if (existing.isPresent()) {
            AlternativeReaction existingReaction = existing.get();
            if (existingReaction.getType() == AlternativeReactionType.LIKE) {
                updatedAt = resolveReactionUpdatedAt(existingReaction, updatedAt);
            } else {
                existingReaction.updateType(AlternativeReactionType.LIKE);
            }
        } else {
            AlternativeReaction savedReaction = alternativeReactionRepository.save(AlternativeReaction.builder()
                .alternative(alternative)
                .user(loginUser)
                .type(reactionType)
                .build());
            updatedAt = resolveReactionUpdatedAt(savedReaction, updatedAt);
        }

        long likesCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        rewardAlternativeAuthorIfEligible(alternative, reactionType, likesCount);
        return AlternativeReactionUpdateResponse.from(altId, reactionType, likesCount, updatedAt);
    }

    public AlternativeReactionResponse getReaction(User user, Long altId) {
        validateInteractionPermission(user);
        findAlternativeById(altId);
        Optional<AlternativeReaction> reaction = alternativeReactionRepository
                .findByAlternative_IdAndUser_Id(altId, user.getId());
        int type = reaction
                .filter(existing -> existing.getType() == AlternativeReactionType.LIKE)
                .map(existing -> AlternativeReactionType.LIKE.code())
                .orElse(AlternativeReactionType.CANCEL.code());
        LocalDateTime updatedAt = reaction
                .filter(existing -> existing.getType() == AlternativeReactionType.LIKE)
                .map(AlternativeReaction::getCreatedAt)
                .orElse(null);
        long likesCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        return AlternativeReactionResponse.from(altId, type, likesCount, updatedAt);
    }

    public AlternativeCommentsResponse getComments(User user, Long altId, Integer page) {
        validateInteractionPermission(user);
        findAlternativeById(altId);
        int pageNumber = normalizePage(page);
        Page<AlternativeComment> commentPage = alternativeCommentRepository.findAllByAlternative_IdAndDeletedAtIsNull(
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
        PatternAlternativeYarn alternative = findAlternativeById(altId);
        User loginUser = userService.getUserById(user.getId());
        AlternativeComment comment = alternativeCommentRepository.save(AlternativeComment.builder()
                .alternative(alternative)
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

    private PatternAlternativeYarn findAlternativeById(Long altId) {
        return patternAlternativeYarnRepository.findById(altId)
                .orElseThrow(AlternativeNotFoundException::new);
    }

    private AlternativeComment findActiveComment(Long altSetId, Long commentId) {
        return alternativeCommentRepository.findByIdAndAlternative_IdAndDeletedAtIsNull(commentId, altSetId)
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

    private void rewardAlternativeAuthorIfEligible(
            PatternAlternativeYarn alternative,
            AlternativeReactionType reactionType,
            long likesCount
    ) {
        if (reactionType != AlternativeReactionType.LIKE) {
            return;
        }
        if (!alternative.canRewardForRecommended(likesCount, CreditPolicy.ALT_YARN_RECOMMEND_REWARD_THRESHOLD)) {
            return;
        }
        creditService.addCredits(
                alternative.getUser(),
                CreditPolicy.ALT_YARN_RECOMMENDED_BALLS,
                CreditTransactionType.ALT_YARN_RECOMMENDED
        );
        alternative.markRecommendedRewarded();
    }

    private LocalDateTime resolveReactionUpdatedAt(AlternativeReaction reaction, LocalDateTime fallback) {
        return reaction == null || reaction.getCreatedAt() == null ? fallback : reaction.getCreatedAt();
    }
}
