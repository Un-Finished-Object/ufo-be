package com.ufo.ufo.domain.alternative.application;

import com.ufo.ufo.domain.alternative.dao.AlternativeCommentRepository;
import com.ufo.ufo.domain.alternative.dao.AlternativeReactionRepository;
import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import com.ufo.ufo.domain.alternative.domain.AlternativeReaction;
import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
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

        if (reactionType.isCancel()) {
            existing.ifPresent(alternativeReactionRepository::delete);
        } else if (existing.isPresent()) {
            existing.get().updateType(reactionType);
        } else {
            alternativeReactionRepository.save(AlternativeReaction.builder()
                .alternative(alternative)
                .user(loginUser)
                .type(reactionType)
                .build());
        }

        long likesCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        rewardAlternativeAuthorIfEligible(alternative, reactionType, likesCount);
        long dislikesCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.DISLIKE);
        return AlternativeReactionUpdateResponse.from(altId, reactionType, likesCount, dislikesCount, LocalDateTime.now());
    }

    public AlternativeReactionResponse getReaction(User user, Long altId) {
        findAlternativeById(altId);
        long likeCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.LIKE);
        long dislikeCount = alternativeReactionRepository.countByAlternative_IdAndType(altId, AlternativeReactionType.DISLIKE);
        return AlternativeReactionResponse.from(altId, likeCount, dislikeCount);
    }

    public AlternativeCommentsResponse getComments(User user, Long altId, Integer page) {
        findAlternativeById(altId);
        int pageNumber = normalizePage(page);
        Page<AlternativeComment> commentPage = alternativeCommentRepository.findAllByAlternative_Id(
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

    private PatternAlternativeYarn findAlternativeById(Long altId) {
        return patternAlternativeYarnRepository.findById(altId)
                .orElseThrow(AlternativeNotFoundException::new);
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
}
