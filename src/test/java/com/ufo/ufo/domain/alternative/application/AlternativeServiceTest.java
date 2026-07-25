package com.ufo.ufo.domain.alternative.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.alternative.dao.AlternativeCommentRepository;
import com.ufo.ufo.domain.alternative.dao.AlternativeReactionRepository;
import com.ufo.ufo.domain.alternative.domain.AlternativeReaction;
import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
import com.ufo.ufo.domain.alternative.exception.AlternativeInteractionPermissionDeniedException;
import com.ufo.ufo.domain.alternative.exception.AlternativeNotFoundException;
import com.ufo.ufo.domain.alternative.exception.InvalidAlternativeReactionTypeException;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.domain.pattern.dao.YarnAlternativeRepository;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnAlternativeFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("대체 실 서비스 테스트")
class AlternativeServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private YarnAlternativeRepository yarnAlternativeRepository;

    @Mock
    private AlternativeReactionRepository alternativeReactionRepository;

    @Mock
    private AlternativeCommentRepository alternativeCommentRepository;

    @InjectMocks
    private AlternativeService alternativeService;

    @Test
    @DisplayName("시스템 등록 대체 실에 반응을 변경해야 한다")
    void updateReaction_UpdatesYarnAlternativeReaction() {
        User user = UserFixture.createUserWithId(1L);
        YarnAlternative yarnAlternative = YarnAlternativeFixture.createWithId(
                12L, YarnFixture.createYarnWithId(1L), YarnFixture.createYarnWithId(2L)
        );
        AlternativeReaction reaction = AlternativeReaction.builder()
                .yarnAlternative(yarnAlternative)
                .user(user)
                .type(AlternativeReactionType.LIKE)
                .build();
        when(yarnAlternativeRepository.findById(12L)).thenReturn(Optional.of(yarnAlternative));
        when(userService.getUserById(1L)).thenReturn(user);
        when(alternativeReactionRepository.findByYarnAlternative_IdAndUser_Id(12L, 1L))
                .thenReturn(Optional.of(reaction));
        when(alternativeReactionRepository.countByYarnAlternative_IdAndType(12L, AlternativeReactionType.LIKE))
                .thenReturn(4L);

        AlternativeReactionUpdateResponse response = alternativeService.updateReaction(
                user, 12L, new UpdateAlternativeReactionRequest(2)
        );

        assertThat(reaction.getYarnAlternative()).isEqualTo(yarnAlternative);
        assertThat(response.type()).isEqualTo(2);
    }

    @Test
    @DisplayName("반응이 없으면 시스템 등록 대체 실의 취소 상태와 좋아요 수를 반환해야 한다")
    void getReaction_ReturnsCancelWhenReactionDoesNotExist() {
        User user = UserFixture.createUserWithId(1L);
        YarnAlternative yarnAlternative = yarnAlternative(13L);
        when(yarnAlternativeRepository.findById(13L)).thenReturn(Optional.of(yarnAlternative));
        when(alternativeReactionRepository.findByYarnAlternative_IdAndUser_Id(13L, 1L))
                .thenReturn(Optional.empty());
        when(alternativeReactionRepository.countByYarnAlternative_IdAndType(13L, AlternativeReactionType.LIKE))
                .thenReturn(3L);

        AlternativeReactionResponse response = alternativeService.getReaction(user, 13L);

        assertThat(response.altSetId()).isEqualTo(13L);
        assertThat(response.type()).isEqualTo(AlternativeReactionType.CANCEL.code());
        assertThat(response.likesCount()).isEqualTo(3L);
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 반응 타입이면 시스템 등록 대체 실을 조회하지 않고 예외가 발생해야 한다")
    void updateReaction_InvalidType_Throws() {
        User user = UserFixture.createUserWithId(1L);

        assertThatThrownBy(() -> alternativeService.updateReaction(
                user, 14L, new UpdateAlternativeReactionRequest(99)
        )).isInstanceOf(InvalidAlternativeReactionTypeException.class);
    }

    @Test
    @DisplayName("게스트 사용자는 시스템 등록 대체 실에 반응하거나 댓글을 작성할 수 없어야 한다")
    void interaction_Guest_Throws() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 99L);

        assertThatThrownBy(() -> alternativeService.updateReaction(
                guest, 15L, new UpdateAlternativeReactionRequest(1)
        )).isInstanceOf(AlternativeInteractionPermissionDeniedException.class);
        assertThatThrownBy(() -> alternativeService.createComment(
                guest, 15L, new CreateAlternativeCommentRequest("comment")
        )).isInstanceOf(AlternativeInteractionPermissionDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 시스템 등록 대체 실에는 댓글을 작성할 수 없어야 한다")
    void createComment_NotFound_Throws() {
        User user = UserFixture.createUserWithId(1L);
        when(yarnAlternativeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alternativeService.createComment(
                user, 99L, new CreateAlternativeCommentRequest("comment")
        )).isInstanceOf(AlternativeNotFoundException.class);
    }

    @Test
    @DisplayName("대체 실 댓글 목록 조회 시 본인 작성 여부(isMine)가 올바르게 계산되어 반환되어야 한다")
    void getComments_ReturnsCommentsWithIsMineFlag() {
        User user1 = UserFixture.createUserWithId(1L);
        User user2 = UserFixture.createUserWithId(2L);
        YarnAlternative yarnAlternative = yarnAlternative(10L);

        AlternativeComment comment1 = AlternativeComment.builder()
                .yarnAlternative(yarnAlternative)
                .user(user1)
                .content("내 댓글")
                .build();
        AlternativeComment comment2 = AlternativeComment.builder()
                .yarnAlternative(yarnAlternative)
                .user(user2)
                .content("다른 사람 댓글")
                .build();

        PageRequest pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        when(yarnAlternativeRepository.findById(10L)).thenReturn(Optional.of(yarnAlternative));
        when(alternativeCommentRepository.findAllByYarnAlternative_IdAndDeletedAtIsNull(10L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(comment1, comment2), pageRequest, 2));

        AlternativeCommentsResponse response = alternativeService.getComments(user1, 10L, 1);

        assertThat(response.comments()).hasSize(2);
        assertThat(response.comments().get(0).isMine()).isTrue();
        assertThat(response.comments().get(1).isMine()).isFalse();
    }

    private YarnAlternative yarnAlternative(Long id) {
        return YarnAlternativeFixture.createWithId(
                id,
                YarnFixture.createYarnWithId(id * 10),
                YarnFixture.createYarnWithId(id * 10 + 1)
        );
    }
}
