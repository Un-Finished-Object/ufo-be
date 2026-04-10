package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.global.exception.ApiException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 구매 서비스 테스트")
class PatternPurchaseServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private CreditService creditService;

    @Mock
    private UserService userService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @InjectMocks
    private PatternPurchaseService patternPurchaseService;

    @Test
    @DisplayName("구매 요청 type이 chat이면 채팅 잠금 해제만 구매해야 한다")
    void purchase_TypeChat_PurchasesChatOnly() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, 20L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.existsByUser_IdAndRoom_Pattern_Id(1L, 10L)).thenReturn(false);
        when(chatRoomRepository.findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
                eq(10L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(Optional.of(room));
        when(chatRoomStatusRepository.save(any(ChatRoomStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PatternPurchaseResponse response = patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo("chat");
        verify(creditService).purchaseUnlock(user, 10L, UnlockType.CHAT);
        verify(creditService, times(1)).purchaseUnlock(user, 10L, UnlockType.CHAT);
        verify(creditService, times(0)).purchaseUnlock(user, 10L, UnlockType.YARN_INFO);
        verify(chatRoomStatusRepository, times(1)).save(any(ChatRoomStatus.class));
    }

    @Test
    @DisplayName("구매 요청 type이 yarn이면 대체 실 잠금 해제만 구매해야 한다")
    void purchase_TypeYarn_PurchasesAlternativeOnly() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        PatternPurchaseResponse response = patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("yarn"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo("yarn");
        verify(creditService, times(0)).purchaseUnlock(user, 10L, UnlockType.CHAT);
        verify(creditService).purchaseUnlock(user, 10L, UnlockType.YARN_INFO);
        verify(creditService, times(1)).purchaseUnlock(user, 10L, UnlockType.YARN_INFO);
        verifyNoInteractions(chatRoomStatusRepository);
        verifyNoInteractions(chatRoomRepository);
    }

    @Test
    @DisplayName("이미 구매한 채팅방이면 예외가 발생해야 한다")
    void purchase_ChatAlreadyPurchased_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.existsByUser_IdAndRoom_Pattern_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ChatRoomAlreadyPurchasedException.class);
    }

    @Test
    @DisplayName("구매 여부 조회는 채팅/대체 실 해금 여부를 반환해야 한다")
    void getStatus_ReturnsUnlockStatus() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(creditService.isUnlocked(1L, 10L, UnlockType.CHAT)).thenReturn(true);
        when(creditService.isUnlocked(1L, 10L, UnlockType.YARN_INFO)).thenReturn(false);

        PatternPurchaseStatusResponse response = patternPurchaseService.getStatus(user, 10L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.chat()).isTrue();
        assertThat(response.alternative()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 도안 구매는 예외가 발생해야 한다")
    void purchase_PatternNotFound_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(creditService);
        verifyNoInteractions(chatRoomStatusRepository);
        verifyNoInteractions(chatRoomRepository);
    }

    @Test
    @DisplayName("삭제된 도안 구매는 예외가 발생해야 한다")
    void purchase_DeletedPattern_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        Pattern deletedPattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(deletedPattern, LocalDateTime.now());
        when(patternRepository.findById(10L)).thenReturn(Optional.of(deletedPattern));

        assertThatThrownBy(() -> patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(creditService);
        verifyNoInteractions(chatRoomStatusRepository);
        verifyNoInteractions(chatRoomRepository);
    }

    @Test
    @DisplayName("구매 요청 type이 유효하지 않으면 예외가 발생해야 한다")
    void purchase_InvalidType_ThrowsException() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> patternPurchaseService.purchase(user, 10L, new PatternPurchaseRequest("9")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(creditService);
        verifyNoInteractions(chatRoomStatusRepository);
        verifyNoInteractions(chatRoomRepository);
    }
}
