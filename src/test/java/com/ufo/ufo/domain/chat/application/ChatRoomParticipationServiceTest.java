package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅방 참여 서비스 테스트")
class ChatRoomParticipationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ChatRoomProvisioningService provisioningService;
    @Mock
    private ChatRoomStatusRepository statusRepository;
    @Mock
    private ChatNicknameGenerator nicknameGenerator;
    @InjectMocks
    private ChatRoomParticipationService participationService;

    private final User user = UserFixture.createUserWithId(1L);
    private final Pattern pattern = PatternFixture.createPatternWithId(10L);
    private final ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, 20L);

    @Test
    @DisplayName("사용자 락, 방 배정, 채팅방 락 순서로 처리한 뒤 닉네임과 참여 정보를 저장해야 한다")
    void joinsAfterLockingUserAndRoom() {
        prepareJoin();
        var saved = ArgumentCaptor.forClass(ChatRoomStatus.class);

        participationService.join(user, pattern);

        var order = inOrder(userService, provisioningService, statusRepository, nicknameGenerator);
        order.verify(userService).getUserByIdForUpdate(1L);
        order.verify(statusRepository).existsByUser_IdAndRoom_Pattern_Id(1L, 10L);
        order.verify(provisioningService).assignJoinableRoom(pattern);
        order.verify(provisioningService).lockRoom(room);
        order.verify(statusRepository).countByRoom_Id(20L);
        order.verify(nicknameGenerator).generate(2L);
        order.verify(statusRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getRoom()).isSameAs(room);
        assertThat(saved.getValue().getNickname()).isEqualTo("버건디 린넨");
        assertThat(saved.getValue().isFavorite()).isFalse();
        assertThat(saved.getValue().isHidden()).isFalse();
    }

    @Test
    @DisplayName("이미 도안의 채팅방에 참여했다면 중복 참여 오류를 반환해야 한다")
    void rejectsExistingParticipation() {
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);
        when(statusRepository.existsByUser_IdAndRoom_Pattern_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> participationService.join(user, pattern))
                .isInstanceOf(ChatRoomAlreadyPurchasedException.class);

        verifyNoInteractions(provisioningService, nicknameGenerator);
    }

    @Test
    @DisplayName("참여 저장 중 제약조건 오류는 기존 구매 중복 오류로 처리해야 한다")
    void translatesParticipationConstraintFailure() {
        prepareJoin();
        when(statusRepository.saveAndFlush(any(ChatRoomStatus.class)))
                .thenThrow(new DataIntegrityViolationException("participation constraint"));

        assertThatThrownBy(() -> participationService.join(user, pattern))
                .isInstanceOf(ChatRoomAlreadyPurchasedException.class);
    }

    @Test
    @DisplayName("최근 참여 내역이 있으면 해당 방 ID만 반환해야 한다")
    void returnsLatestJoinedRoomId() {
        when(statusRepository.findFirstByUser_IdAndRoom_Pattern_IdOrderByCreatedAtDescIdDesc(1L, 10L))
                .thenReturn(Optional.of(ChatRoomStatus.builder().user(user).room(room).build()));

        assertThat(participationService.findLatestJoinedRoomId(1L, 10L)).contains(20L);

        verifyNoInteractions(userService, provisioningService, nicknameGenerator);
    }

    @Test
    @DisplayName("참여 내역이 없으면 빈 결과를 반환해야 한다")
    void returnsEmptyWithoutParticipation() {
        when(statusRepository.findFirstByUser_IdAndRoom_Pattern_IdOrderByCreatedAtDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());

        assertThat(participationService.findLatestJoinedRoomId(1L, 10L)).isEmpty();
    }

    private void prepareJoin() {
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);
        when(provisioningService.assignJoinableRoom(pattern)).thenReturn(room);
        when(provisioningService.lockRoom(room)).thenReturn(room);
        when(statusRepository.countByRoom_Id(20L)).thenReturn(2L);
        when(nicknameGenerator.generate(2L)).thenReturn("버건디 린넨");
    }
}
