package com.ufo.ufo.domain.alternative.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.alternative.application.AlternativeService;
import com.ufo.ufo.domain.alternative.dto.request.CreateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeCommentRequest;
import com.ufo.ufo.domain.alternative.dto.request.UpdateAlternativeReactionRequest;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentCreateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentDeleteResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentUpdateResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeCommentsResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionResponse;
import com.ufo.ufo.domain.alternative.dto.response.AlternativeReactionUpdateResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("대체 실 컨트롤러 테스트")
class AlternativeControllerTest {

    @Mock
    private AlternativeService alternativeService;

    @InjectMocks
    private AlternativeController alternativeController;

    @Test
    @DisplayName("반응 업데이트 API는 서비스 응답을 data에 담아 반환해야 한다")
    void updateReaction_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        UpdateAlternativeReactionRequest request = new UpdateAlternativeReactionRequest(1);
        when(alternativeService.updateReaction(user, 7L, request))
                .thenReturn(new AlternativeReactionUpdateResponse(7L, 1, 3, LocalDateTime.now()));

        ResponseEntity<ApiResponse<AlternativeReactionUpdateResponse>> response =
                alternativeController.updateReaction(user, 7L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().type()).isEqualTo(1);
        verify(alternativeService).updateReaction(user, 7L, request);
    }

    @Test
    @DisplayName("반응 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getReaction_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(alternativeService.getReaction(user, 7L))
                .thenReturn(new AlternativeReactionResponse(7L, 1, 3, LocalDateTime.now()));

        ResponseEntity<ApiResponse<AlternativeReactionResponse>> response =
                alternativeController.getReaction(user, 7L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().likesCount()).isEqualTo(3);
        assertThat(response.getBody().data().type()).isEqualTo(1);
        verify(alternativeService).getReaction(user, 7L);
    }

    @Test
    @DisplayName("댓글 목록 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getComments_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        AlternativeCommentsResponse.Comment item = new AlternativeCommentsResponse.Comment(
                3L, "hello", "tester", LocalDateTime.now()
        );
        when(alternativeService.getComments(user, 7L, 1))
                .thenReturn(new AlternativeCommentsResponse(7L, java.util.List.of(item), 1, 0));

        ResponseEntity<ApiResponse<AlternativeCommentsResponse>> response =
                alternativeController.getComments(user, 7L, 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().comments()).hasSize(1);
        assertThat(response.getBody().data().page()).isEqualTo(1);
        assertThat(response.getBody().data().nextPage()).isEqualTo(0);
        verify(alternativeService).getComments(user, 7L, 1);
    }

    @Test
    @DisplayName("댓글 등록 API는 서비스 응답을 data에 담아 반환해야 한다")
    void createComment_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        CreateAlternativeCommentRequest request = new CreateAlternativeCommentRequest("hello");
        AlternativeCommentCreateResponse item = new AlternativeCommentCreateResponse(
                7L, 3L, "hello", "tester", LocalDateTime.now()
        );
        when(alternativeService.createComment(user, 7L, request)).thenReturn(item);

        ResponseEntity<ApiResponse<AlternativeCommentCreateResponse>> response =
                alternativeController.createComment(user, 7L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().altSetId()).isEqualTo(7L);
        assertThat(response.getBody().data().commentId()).isEqualTo(3L);
        assertThat(response.getBody().data().username()).isEqualTo("tester");
        assertThat(response.getBody().data().content()).isEqualTo("hello");
        verify(alternativeService).createComment(user, 7L, request);
    }

    @Test
    @DisplayName("댓글 수정 API는 서비스 응답을 data에 담아 반환해야 한다")
    void updateComment_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        UpdateAlternativeCommentRequest request = new UpdateAlternativeCommentRequest("updated");
        AlternativeCommentUpdateResponse item = new AlternativeCommentUpdateResponse(
                7L, 3L, "updated", "tester", LocalDateTime.now()
        );
        when(alternativeService.updateComment(user, 7L, 3L, request)).thenReturn(item);

        ResponseEntity<ApiResponse<AlternativeCommentUpdateResponse>> response =
                alternativeController.updateComment(user, 7L, 3L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().commentId()).isEqualTo(3L);
        assertThat(response.getBody().data().content()).isEqualTo("updated");
        verify(alternativeService).updateComment(user, 7L, 3L, request);
    }

    @Test
    @DisplayName("댓글 삭제 API는 서비스 응답을 data에 담아 반환해야 한다")
    void deleteComment_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        AlternativeCommentDeleteResponse item = new AlternativeCommentDeleteResponse(
                7L, 3L, LocalDateTime.now()
        );
        when(alternativeService.deleteComment(user, 7L, 3L)).thenReturn(item);

        ResponseEntity<ApiResponse<AlternativeCommentDeleteResponse>> response =
                alternativeController.deleteComment(user, 7L, 3L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().commentId()).isEqualTo(3L);
        assertThat(response.getBody().data().deletedAt()).isNotNull();
        verify(alternativeService).deleteComment(user, 7L, 3L);
    }
}
