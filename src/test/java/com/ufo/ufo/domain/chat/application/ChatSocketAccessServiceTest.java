package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.exception.ChatNicknameNotFoundException;
import com.ufo.ufo.domain.user.dao.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSocketAccessServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatSocketEventPublisher eventPublisher;

    @InjectMocks
    private ChatSocketAccessService accessService;

    @Test
    void missingPrincipalSendsUnauthorizedErrorWithoutUserLookup() {
        assertThat(accessService.resolveUser(null, 10L, "temp-1")).isEmpty();

        verify(eventPublisher).sendError(10L, "UNAUTHORIZED", "인증 사용자 정보를 확인할 수 없습니다.", "temp-1");
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingMembershipSendsForbiddenError() {
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(21L, 10L)).thenReturn(Optional.empty());

        assertThat(accessService.resolveRoomStatus(21L, 10L, "temp-1")).isEmpty();

        verify(eventPublisher).sendError(10L, "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", "temp-1");
    }

    @Test
    void blankReplySenderNicknameThrows() {
        ChatRoomStatus status = ChatRoomStatus.builder().nickname(" ").build();
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(22L, 10L)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> accessService.getChatNickname(22L, 10L))
                .isInstanceOf(ChatNicknameNotFoundException.class);
    }
}
