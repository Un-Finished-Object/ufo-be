package com.ufo.ufo.domain.alternative.application;

import com.ufo.ufo.global.security.types.Role;
import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ufo.ufo.domain.alternative.exception.InvalidAlternativeReactionTypeException;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternAlternativeYarnFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("대체 실 서비스 테스트")
class AlternativeServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private CreditService creditService;

    @Mock
    private PatternAlternativeYarnRepository patternAlternativeYarnRepository;

    @Mock
    private AlternativeReactionRepository alternativeReactionRepository;

    @Mock
    private AlternativeCommentRepository alternativeCommentRepository;

    @InjectMocks
    private AlternativeService alternativeService;

    @Test
    @DisplayName("추천 수가 5개를 초과하면 작성자에게 볼을 1회 지급해야 한다")
    void updateReaction_RewardsAuthorOnceWhenLikesExceedThreshold() {
        User reactor = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                10L,
                PatternFixture.createPatternWithId(100L),
                author,
                YarnFixture.createYarnWithId(20L)
        );

        when(patternAlternativeYarnRepository.findById(10L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(reactor);
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        when(alternativeReactionRepository.countByAlternative_IdAndType(
                10L, com.ufo.ufo.domain.alternative.domain.AlternativeReactionType.LIKE)).thenReturn(6L);
        when(alternativeReactionRepository.countByAlternative_IdAndType(
                10L, com.ufo.ufo.domain.alternative.domain.AlternativeReactionType.DISLIKE)).thenReturn(1L);

        AlternativeReactionUpdateResponse response =
                alternativeService.updateReaction(reactor, 10L, new UpdateAlternativeReactionRequest(1));

        assertThat(response.likesCount()).isEqualTo(6L);
        verify(creditService).addCredits(author, CreditPolicy.ALT_YARN_RECOMMENDED_BALLS, CreditTransactionType.ALT_YARN_RECOMMENDED);
        assertThat(alternative.getRecommendedRewardedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 보상 지급된 대체 실은 추천 수가 증가해도 다시 지급되지 않아야 한다")
    void updateReaction_DoesNotRewardAgainWhenAlreadyRewarded() {
        User reactor = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                11L,
                PatternFixture.createPatternWithId(101L),
                author,
                YarnFixture.createYarnWithId(21L)
        );
        alternative.markRecommendedRewarded();

        when(patternAlternativeYarnRepository.findById(11L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(reactor);
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(11L, 1L)).thenReturn(Optional.empty());
        when(alternativeReactionRepository.countByAlternative_IdAndType(
                11L, com.ufo.ufo.domain.alternative.domain.AlternativeReactionType.LIKE)).thenReturn(7L);
        when(alternativeReactionRepository.countByAlternative_IdAndType(
                11L, com.ufo.ufo.domain.alternative.domain.AlternativeReactionType.DISLIKE)).thenReturn(0L);

        alternativeService.updateReaction(reactor, 11L, new UpdateAlternativeReactionRequest(1));

        verify(creditService, never()).addCredits(any(), anyInt(), any());
    }

    @Test
    @DisplayName("취소 반응은 기존 반응을 삭제해야 한다")
    void updateReaction_Cancel_DeletesExistingReaction() {
        User reactor = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                12L,
                PatternFixture.createPatternWithId(102L),
                author,
                YarnFixture.createYarnWithId(22L)
        );
        AlternativeReaction existing = AlternativeReaction.builder()
                .alternative(alternative)
                .user(reactor)
                .type(AlternativeReactionType.LIKE)
                .build();

        when(patternAlternativeYarnRepository.findById(12L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(reactor);
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(12L, 1L)).thenReturn(Optional.of(existing));
        when(alternativeReactionRepository.countByAlternative_IdAndType(12L, AlternativeReactionType.LIKE)).thenReturn(4L);
        when(alternativeReactionRepository.countByAlternative_IdAndType(12L, AlternativeReactionType.DISLIKE)).thenReturn(1L);

        AlternativeReactionUpdateResponse response =
                alternativeService.updateReaction(reactor, 12L, new UpdateAlternativeReactionRequest(3));

        verify(alternativeReactionRepository).delete(existing);
        verify(creditService, never()).addCredits(any(), anyInt(), any());
        assertThat(response.type()).isEqualTo(3);
    }

    @Test
    @DisplayName("비추천 반응은 작성자 보상을 지급하지 않아야 한다")
    void updateReaction_Dislike_DoesNotRewardAuthor() {
        User reactor = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                13L,
                PatternFixture.createPatternWithId(103L),
                author,
                YarnFixture.createYarnWithId(23L)
        );

        when(patternAlternativeYarnRepository.findById(13L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(reactor);
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(13L, 1L)).thenReturn(Optional.empty());
        when(alternativeReactionRepository.countByAlternative_IdAndType(13L, AlternativeReactionType.LIKE)).thenReturn(10L);
        when(alternativeReactionRepository.countByAlternative_IdAndType(13L, AlternativeReactionType.DISLIKE)).thenReturn(2L);

        AlternativeReactionUpdateResponse response =
                alternativeService.updateReaction(reactor, 13L, new UpdateAlternativeReactionRequest(2));

        verify(creditService, never()).addCredits(any(), anyInt(), any());
        assertThat(response.type()).isEqualTo(2);
    }

    @Test
    @DisplayName("반응 타입이 유효하지 않으면 예외가 발생해야 한다")
    void updateReaction_InvalidType_Throws() {
        User reactor = UserFixture.createUserWithId(1L);

        assertThatThrownBy(() -> alternativeService.updateReaction(reactor, 14L, new UpdateAlternativeReactionRequest(99)))
                .isInstanceOf(InvalidAlternativeReactionTypeException.class);
    }

    @Test
    @DisplayName("게스트 사용자는 반응을 업데이트할 수 없어야 한다")
    void updateReaction_Guest_ThrowsForbidden() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 99L);

        assertThatThrownBy(() -> alternativeService.updateReaction(guest, 1L, new UpdateAlternativeReactionRequest(1)))
                .isInstanceOf(AlternativeInteractionPermissionDeniedException.class);
    }

    @Test
    @DisplayName("반응 조회는 좋아요/싫어요 집계를 반환해야 한다")
    void getReaction_ReturnsCounts() {
        User user = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                20L,
                PatternFixture.createPatternWithId(200L),
                author,
                YarnFixture.createYarnWithId(30L)
        );
        when(patternAlternativeYarnRepository.findById(20L)).thenReturn(Optional.of(alternative));
        when(alternativeReactionRepository.countByAlternative_IdAndType(20L, AlternativeReactionType.LIKE)).thenReturn(8L);
        when(alternativeReactionRepository.countByAlternative_IdAndType(20L, AlternativeReactionType.DISLIKE)).thenReturn(3L);

        AlternativeReactionResponse response = alternativeService.getReaction(user, 20L);

        assertThat(response.likeCount()).isEqualTo(8L);
        assertThat(response.dislikeCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("댓글 목록 조회에서 page가 1보다 작으면 1페이지로 보정해야 한다")
    void getComments_NormalizesPageAndReturnsItems() {
        User user = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                21L,
                PatternFixture.createPatternWithId(201L),
                author,
                YarnFixture.createYarnWithId(31L)
        );
        AlternativeComment comment = AlternativeComment.builder()
                .alternative(alternative)
                .user(author)
                .content("hello")
                .build();

        when(patternAlternativeYarnRepository.findById(21L)).thenReturn(Optional.of(alternative));
        when(alternativeCommentRepository.findAllByAlternative_Id(eq(21L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));

        AlternativeCommentsResponse response = alternativeService.getComments(user, 21L, 0);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().getFirst().content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("댓글 등록은 altId/content/username을 응답해야 한다")
    void createComment_ReturnsMappedResponse() {
        User user = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                22L,
                PatternFixture.createPatternWithId(202L),
                loginUser,
                YarnFixture.createYarnWithId(32L)
        );
        AlternativeComment saved = AlternativeComment.builder()
                .alternative(alternative)
                .user(loginUser)
                .content("comment body")
                .build();
        setCreatedAt(saved, LocalDateTime.now());

        when(patternAlternativeYarnRepository.findById(22L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(alternativeCommentRepository.save(any(AlternativeComment.class))).thenReturn(saved);

        AlternativeCommentCreateResponse response =
                alternativeService.createComment(user, 22L, new CreateAlternativeCommentRequest("comment body"));

        assertThat(response.altId()).isEqualTo(22L);
        assertThat(response.content()).isEqualTo("comment body");
        assertThat(response.username()).isEqualTo(loginUser.getNickname());
    }

    @Test
    @DisplayName("게스트 사용자는 댓글을 작성할 수 없어야 한다")
    void createComment_Guest_ThrowsForbidden() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 100L);

        assertThatThrownBy(() -> alternativeService.createComment(guest, 1L, new CreateAlternativeCommentRequest("x")))
                .isInstanceOf(AlternativeInteractionPermissionDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 대체 실에 댓글 등록 시 예외가 발생해야 한다")
    void createComment_NotFound_Throws() {
        User user = UserFixture.createUserWithId(1L);
        when(patternAlternativeYarnRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alternativeService.createComment(user, 999L, new CreateAlternativeCommentRequest("x")))
                .isInstanceOf(AlternativeNotFoundException.class);
    }

    private void setCreatedAt(AlternativeComment comment, LocalDateTime createdAt) {
        try {
            Field createdAtField = comment.getClass().getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(comment, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
