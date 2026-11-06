package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.application.ChatRoomParticipationService;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.global.exception.ApiException;
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
    private ChatRoomParticipationService participationService;
    @InjectMocks
    private PatternPurchaseService purchaseService;

    @Test
    @DisplayName("채팅 해금 구매 후 채팅 도메인에 참여 처리를 위임해야 한다")
    void purchasesChatBeforeJoining() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(creditService.purchaseUnlock(user, 10L, UnlockType.CHAT)).thenReturn(true);

        PatternPurchaseResponse response = purchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo("chat");
        var order = inOrder(creditService, participationService);
        order.verify(creditService).purchaseUnlock(user, 10L, UnlockType.CHAT);
        order.verify(participationService).join(user, pattern);
        verify(creditService, never()).purchaseUnlock(user, 10L, UnlockType.YARN_INFO);
    }

    @Test
    @DisplayName("대체 실 구매는 채팅 참여를 요청하지 않아야 한다")
    void purchasesYarnWithoutJoining() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));

        PatternPurchaseResponse response = purchaseService.purchase(user, 10L, new PatternPurchaseRequest("yarn"));

        assertThat(response.type()).isEqualTo("yarn");
        verify(creditService).purchaseUnlock(user, 10L, UnlockType.YARN_INFO);
        verify(creditService, never()).purchaseUnlock(user, 10L, UnlockType.CHAT);
        verifyNoInteractions(participationService);
    }

    @Test
    @DisplayName("이미 채팅을 해금했다면 참여 처리를 요청하지 않고 중복 오류를 반환해야 한다")
    void rejectsAlreadyUnlockedChat() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));
        when(creditService.purchaseUnlock(user, 10L, UnlockType.CHAT)).thenReturn(false);

        assertThatThrownBy(() -> purchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ChatRoomAlreadyPurchasedException.class);

        verifyNoInteractions(participationService);
    }

    @Test
    @DisplayName("채팅 참여 중 오류가 발생하면 구매 처리도 실패해야 한다")
    void propagatesParticipationFailure() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(creditService.purchaseUnlock(user, 10L, UnlockType.CHAT)).thenReturn(true);
        doThrow(new ChatRoomAlreadyPurchasedException()).when(participationService).join(user, pattern);

        assertThatThrownBy(() -> purchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ChatRoomAlreadyPurchasedException.class);
    }

    @Test
    @DisplayName("구매 여부와 채팅 도메인에서 조회한 최근 참여 방 ID를 반환해야 한다")
    void returnsUnlocksAndLatestRoomId() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));
        when(participationService.findLatestJoinedRoomId(1L, 10L)).thenReturn(Optional.of(20L));
        when(creditService.isUnlocked(1L, 10L, UnlockType.CHAT)).thenReturn(true);

        PatternPurchaseStatusResponse response = purchaseService.getStatus(user, 10L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.chat()).isTrue();
        assertThat(response.alternative()).isFalse();
        assertThat(response.chatRoomId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("참여 내역이 없으면 구매 조회의 방 ID는 null이어야 한다")
    void returnsNullWithoutJoinedRoom() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));
        when(participationService.findLatestJoinedRoomId(1L, 10L)).thenReturn(Optional.empty());

        PatternPurchaseStatusResponse response = purchaseService.getStatus(user, 10L);

        assertThat(response.chat()).isFalse();
        assertThat(response.alternative()).isFalse();
        assertThat(response.chatRoomId()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 도안은 구매 요청을 거부해야 한다")
    void rejectsMissingPattern() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(creditService, participationService);
    }

    @Test
    @DisplayName("삭제된 도안은 구매 요청을 거부해야 한다")
    void rejectsDeletedPattern() {
        var user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(pattern, LocalDateTime.now());
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> purchaseService.purchase(user, 10L, new PatternPurchaseRequest("chat")))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(creditService, participationService);
    }

    @Test
    @DisplayName("유효하지 않은 구매 유형은 구매 요청을 거부해야 한다")
    void rejectsInvalidType() {
        var user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));

        assertThatThrownBy(() -> purchaseService.purchase(user, 10L, new PatternPurchaseRequest("9")))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(creditService, participationService);
    }
}
