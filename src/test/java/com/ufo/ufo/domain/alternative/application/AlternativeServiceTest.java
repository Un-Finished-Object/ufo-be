package com.ufo.ufo.domain.alternative.application;

import com.ufo.ufo.global.security.types.Role;
import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentDeleteResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentUpdateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
import com.ufo.ufo.domain.alternative.exception.AlternativeCommentPermissionDeniedException;
import com.ufo.ufo.domain.alternative.exception.AlternativeInteractionPermissionDeniedException;
import com.ufo.ufo.domain.alternative.exception.AlternativeNotFoundException;
import com.ufo.ufo.domain.alternative.exception.InvalidAlternativeReactionTypeException;
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
    private PatternAlternativeYarnRepository patternAlternativeYarnRepository;

    @Mock
    private AlternativeReactionRepository alternativeReactionRepository;

    @Mock
    private AlternativeCommentRepository alternativeCommentRepository;

    @InjectMocks
    private AlternativeService alternativeService;

    @Test
    @DisplayName("취소 반응은 기존 반응을 취소 상태로 변경하고 변경 시각을 보존해야 한다")
    void updateReaction_Cancel_PreservesCancelledStateAndTimestamp() {
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

        AlternativeReactionUpdateResponse response =
                alternativeService.updateReaction(reactor, 12L, new UpdateAlternativeReactionRequest(2));

        verify(alternativeReactionRepository, never()).delete(existing);
        assertThat(existing.getType()).isEqualTo(AlternativeReactionType.CANCEL);
        assertThat(response.type()).isEqualTo(2);
        assertThat(response.updatedAt()).isEqualTo(existing.getUpdatedAt());
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
    @DisplayName("반응 조회는 현재 사용자의 추천 상태와 전체 추천 수를 반환해야 한다")
    void getReaction_ReturnsCurrentUserStateAndLikeCount() {
        User user = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                20L,
                PatternFixture.createPatternWithId(200L),
                author,
                YarnFixture.createYarnWithId(30L)
        );
        AlternativeReaction reaction = AlternativeReaction.builder()
                .alternative(alternative)
                .user(user)
                .type(AlternativeReactionType.LIKE)
                .build();
        LocalDateTime reactedAt = reaction.getUpdatedAt();
        when(patternAlternativeYarnRepository.findById(20L)).thenReturn(Optional.of(alternative));
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(20L, 1L))
                .thenReturn(Optional.of(reaction));
        when(alternativeReactionRepository.countByAlternative_IdAndType(20L, AlternativeReactionType.LIKE)).thenReturn(8L);

        AlternativeReactionResponse response = alternativeService.getReaction(user, 20L);

        assertThat(response.altSetId()).isEqualTo(20L);
        assertThat(response.type()).isEqualTo(1);
        assertThat(response.likesCount()).isEqualTo(8L);
        assertThat(response.updatedAt()).isEqualTo(reactedAt);
    }

    @Test
    @DisplayName("취소 반응 조회는 취소 시각을 반환해야 한다")
    void getReaction_Cancel_ReturnsCancellationTimestamp() {
        User user = UserFixture.createUserWithId(1L);
        User author = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                26L,
                PatternFixture.createPatternWithId(206L),
                author,
                YarnFixture.createYarnWithId(36L)
        );
        AlternativeReaction reaction = AlternativeReaction.builder()
                .alternative(alternative)
                .user(user)
                .type(AlternativeReactionType.CANCEL)
                .build();

        when(patternAlternativeYarnRepository.findById(26L)).thenReturn(Optional.of(alternative));
        when(alternativeReactionRepository.findByAlternative_IdAndUser_Id(26L, 1L))
                .thenReturn(Optional.of(reaction));
        when(alternativeReactionRepository.countByAlternative_IdAndType(26L, AlternativeReactionType.LIKE))
                .thenReturn(4L);

        AlternativeReactionResponse response = alternativeService.getReaction(user, 26L);

        assertThat(response.type()).isEqualTo(2);
        assertThat(response.likesCount()).isEqualTo(4L);
        assertThat(response.updatedAt()).isEqualTo(reaction.getUpdatedAt());
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
        setCommentId(comment, 1L);

        when(patternAlternativeYarnRepository.findById(21L)).thenReturn(Optional.of(alternative));
        when(alternativeCommentRepository.findAllByAlternative_IdAndDeletedAtIsNull(eq(21L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));

        AlternativeCommentsResponse response = alternativeService.getComments(user, 21L, 0);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().getFirst().commentId()).isEqualTo(1L);
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
        setCommentId(saved, 2L);
        setCreatedAt(saved, LocalDateTime.now());

        when(patternAlternativeYarnRepository.findById(22L)).thenReturn(Optional.of(alternative));
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(alternativeCommentRepository.save(any(AlternativeComment.class))).thenReturn(saved);

        AlternativeCommentCreateResponse response =
                alternativeService.createComment(user, 22L, new CreateAlternativeCommentRequest("comment body"));

        assertThat(response.altSetId()).isEqualTo(22L);
        assertThat(response.commentId()).isEqualTo(2L);
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

    @Test
    @DisplayName("댓글 작성자는 댓글 내용을 수정할 수 있어야 한다")
    void updateComment_Owner_UpdatesContent() {
        User author = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                23L,
                PatternFixture.createPatternWithId(203L),
                author,
                YarnFixture.createYarnWithId(33L)
        );
        AlternativeComment comment = AlternativeComment.builder()
                .alternative(alternative)
                .user(author)
                .content("before")
                .build();
        setCommentId(comment, 3L);

        when(patternAlternativeYarnRepository.findById(23L)).thenReturn(Optional.of(alternative));
        when(alternativeCommentRepository.findByIdAndAlternative_IdAndDeletedAtIsNull(3L, 23L))
                .thenReturn(Optional.of(comment));

        AlternativeCommentUpdateResponse response = alternativeService.updateComment(
                author,
                23L,
                3L,
                new UpdateAlternativeCommentRequest("after")
        );

        assertThat(response.altSetId()).isEqualTo(23L);
        assertThat(response.commentId()).isEqualTo(3L);
        assertThat(response.content()).isEqualTo("after");
        assertThat(response.updatedAt()).isNotNull();

        AlternativeCommentUpdateResponse repeatedResponse = alternativeService.updateComment(
                author,
                23L,
                3L,
                new UpdateAlternativeCommentRequest("after")
        );

        assertThat(repeatedResponse.updatedAt()).isEqualTo(response.updatedAt());
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 댓글을 수정할 수 없어야 한다")
    void updateComment_NotOwner_ThrowsForbidden() {
        User author = UserFixture.createUserWithId(1L);
        User other = UserFixture.createUserWithId(2L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                24L,
                PatternFixture.createPatternWithId(204L),
                author,
                YarnFixture.createYarnWithId(34L)
        );
        AlternativeComment comment = AlternativeComment.builder()
                .alternative(alternative)
                .user(author)
                .content("before")
                .build();
        setCommentId(comment, 4L);

        when(patternAlternativeYarnRepository.findById(24L)).thenReturn(Optional.of(alternative));
        when(alternativeCommentRepository.findByIdAndAlternative_IdAndDeletedAtIsNull(4L, 24L))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> alternativeService.updateComment(
                other,
                24L,
                4L,
                new UpdateAlternativeCommentRequest("after")
        )).isInstanceOf(AlternativeCommentPermissionDeniedException.class);
    }

    @Test
    @DisplayName("댓글 작성자는 댓글을 소프트 삭제할 수 있어야 한다")
    void deleteComment_Owner_SoftDeletesComment() {
        User author = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                25L,
                PatternFixture.createPatternWithId(205L),
                author,
                YarnFixture.createYarnWithId(35L)
        );
        AlternativeComment comment = AlternativeComment.builder()
                .alternative(alternative)
                .user(author)
                .content("comment")
                .build();
        setCommentId(comment, 5L);

        when(patternAlternativeYarnRepository.findById(25L)).thenReturn(Optional.of(alternative));
        when(alternativeCommentRepository.findByIdAndAlternative_IdAndDeletedAtIsNull(5L, 25L))
                .thenReturn(Optional.of(comment));

        AlternativeCommentDeleteResponse response = alternativeService.deleteComment(author, 25L, 5L);

        assertThat(response.altSetId()).isEqualTo(25L);
        assertThat(response.commentId()).isEqualTo(5L);
        assertThat(response.deletedAt()).isNotNull();
        assertThat(comment.getDeletedAt()).isEqualTo(response.deletedAt());
    }

    private void setCreatedAt(Object entity, LocalDateTime createdAt) {
        try {
            Field createdAtField = entity.getClass().getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(entity, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setCommentId(AlternativeComment comment, Long commentId) {
        try {
            Field idField = AlternativeComment.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(comment, commentId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
