package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.response.AdminCheckChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminDeleteChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomInfo;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage;
import com.ufo.ufo.domain.chat.exception.ChatRoomForbiddenException;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
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
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 채팅 서비스 테스트")
class AdminChatServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;

    @Mock
    private UserService userService;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private AdminChatService adminChatService;

    @Test
    @DisplayName("미확인 채팅방 목록 조회 시 정상적으로 DTO 목록을 반환해야 한다")
    void getUnreadChatRooms_ReturnsPagedUnreadChatRooms() {
        User adminUser = createAdminUser(999L);
        AdminUnreadChatRoomInfo info = new AdminUnreadChatRoomInfo(
                101L,
                1L,
                "포근한 라운드넥 스웨터",
                "/images/patterns/1.jpg",
                10L,
                LocalDateTime.of(2026, 7, 21, 14, 32),
                LocalDateTime.of(2026, 7, 10, 9, 0)
        );

        when(userService.getUserById(999L)).thenReturn(adminUser);
        when(chatRoomRepository.findUnreadChatRoomsForAdmin(999L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(info), PageRequest.of(0, 20), 1));
        when(chatMessageRepository.findLatestMessagesByRoomIds(List.of(101L)))
                .thenReturn(List.of(new ChatRoomLastMessage(101L, "감사합니다. 저도 42단까지 떠볼게요.")));
        when(imageService.buildImageUrl("/images/patterns/1.jpg")).thenReturn("http://cdn.com/images/patterns/1.jpg");

        AdminUnreadChatRoomListResponse response = adminChatService.getUnreadChatRooms(adminUser, 1);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPages()).isEqualTo(0);
        assertThat(response.chats()).hasSize(1);
        assertThat(response.chats().getFirst().chatId()).isEqualTo(101L);
        assertThat(response.chats().getFirst().chatName()).isEqualTo("포근한 라운드넥 스웨터");
        assertThat(response.chats().getFirst().chatImageUrl()).isEqualTo("http://cdn.com/images/patterns/1.jpg");
        assertThat(response.chats().getFirst().unRead()).isEqualTo(10);
        assertThat(response.chats().getFirst().lastMessage()).isEqualTo("감사합니다. 저도 42단까지 떠볼게요.");
    }

    @Test
    @DisplayName("채팅 메시지 삭제 시 deletedAt이 기록되어야 한다")
    void deleteMessage_Success() {
        User adminUser = createAdminUser(999L);
        Long roomId = 19L;
        Long messageId = 38L;

        Pattern pattern = PatternFixture.createPatternWithId(1L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatMessage message = ChatMessage.builder()
                .room(room)
                .user(UserFixture.createUserWithId(2L))
                .text("부적절한 메시지")
                .build();
        setId(message, messageId);

        when(userService.getUserById(999L)).thenReturn(adminUser);
        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByIdAndRoom_Id(messageId, roomId)).thenReturn(Optional.of(message));

        AdminDeleteChatMessageResponse response = adminChatService.deleteMessage(adminUser, roomId, messageId);

        assertThat(response.chatRoomId()).isEqualTo(roomId);
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.deletedAt()).isNotNull();
        assertThat(message.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 메시지 삭제 요청 시 InvalidChatMessageIdException 예외가 발생해야 한다")
    void deleteMessage_NotFound_ThrowsException() {
        User adminUser = createAdminUser(999L);
        Long roomId = 19L;
        Long messageId = 999L;
        Pattern pattern = PatternFixture.createPatternWithId(1L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);

        when(userService.getUserById(999L)).thenReturn(adminUser);
        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByIdAndRoom_Id(messageId, roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminChatService.deleteMessage(adminUser, roomId, messageId))
                .isInstanceOf(InvalidChatMessageIdException.class);
    }

    @Test
    @DisplayName("채팅 메시지 확인 처리 시 ChatReadStatus가 신규 생성/갱신되어야 한다")
    void checkMessage_Success() {
        User adminUser = createAdminUser(999L);
        Long roomId = 19L;
        Long messageId = 38L;

        Pattern pattern = PatternFixture.createPatternWithId(1L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatMessage message = ChatMessage.builder()
                .room(room)
                .user(UserFixture.createUserWithId(2L))
                .text("확인 대상 메시지")
                .build();

        when(userService.getUserById(999L)).thenReturn(adminUser);
        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByIdAndRoom_Id(messageId, roomId)).thenReturn(Optional.of(message));
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 999L)).thenReturn(Optional.empty());

        AdminCheckChatMessageResponse response = adminChatService.checkMessage(adminUser, roomId, messageId);

        assertThat(response.chatRoomId()).isEqualTo(roomId);
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.checkedAt()).isNotNull();

        verify(chatReadStatusRepository).save(any(ChatReadStatus.class));
    }

    @Test
    @DisplayName("관리자 권한(ROLE_ADMIN)이 아닌 사용자가 요청 시 ChatRoomForbiddenException 예외가 발생해야 한다")
    void validateAdminRole_NonAdmin_ThrowsException() {
        User normalUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(normalUser);

        assertThatThrownBy(() -> adminChatService.getUnreadChatRooms(normalUser, 1))
                .isInstanceOf(ChatRoomForbiddenException.class);
    }

    private User createAdminUser(Long id) {
        User admin = User.builder()
                .email("admin@ufo.com")
                .nickname("어드민")
                .role(Role.ROLE_ADMIN)
                .build();
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(admin, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return admin;
    }

    private void setId(ChatMessage message, Long id) {
        try {
            Field idField = ChatMessage.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(message, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
