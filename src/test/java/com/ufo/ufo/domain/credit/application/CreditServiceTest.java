package com.ufo.ufo.domain.credit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.dao.CreditTransactionRepository;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditWalletResponse;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("크레딧 서비스 테스트")
class CreditServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private UnlockRepository unlockRepository;

    @InjectMocks
    private CreditService creditService;

    @Test
    @DisplayName("내 크레딧 잔액 조회는 DB 사용자 잔액을 반환해야 한다")
    void getWallet_ReturnsUserBalance() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        loginUser.addCredits(27);
        when(userService.getUserById(1L)).thenReturn(loginUser);

        CreditWalletResponse response = creditService.getWallet(requestUser);

        assertThat(response.balance()).isEqualTo(27);
    }

    @Test
    @DisplayName("크레딧 변동 내역 조회는 최신순 로그를 응답 DTO로 변환해야 한다")
    void getTransactions_ReturnsMappedTransactions() {
        User user = UserFixture.createUserWithId(1L);
        user.addCredits(11);
        when(userService.getUserById(1L)).thenReturn(user);
        CreditTransaction transaction = CreditTransaction.builder()
                .user(user)
                .amount(-1)
                .type(CreditTransactionType.CHATROOM_ENTRY)
                .build();
        setTransactionId(transaction, 1L);
        when(creditTransactionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<CreditTransaction>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));

        CreditTransactionsResponse response = creditService.getTransactions(user, "SPEND", "CHATROOM_ENTRY", 1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("ct_1");
        assertThat(response.items().getFirst().type()).isEqualTo("SPEND");
        assertThat(response.items().getFirst().reason()).isEqualTo("CHATROOM_ENTRY");
        assertThat(response.items().getFirst().amount()).isEqualTo(1);
        assertThat(response.items().getFirst().balanceAfter()).isEqualTo(11);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    @DisplayName("크레딧 정책 조회는 일일 획득 한도/획득/사용 규칙을 반환해야 한다")
    void getRules_ReturnsConfiguredRules() {
        CreditRulesResponse response = creditService.getRules();

        assertThat(response.dailyMaxEarnCredits()).isEqualTo(20);
        assertThat(response.earnRules()).isNotEmpty();
        assertThat(response.spendRules()).isNotEmpty();
    }

    @Test
    @DisplayName("크레딧 추가는 사용자 잔액을 올리고 로그를 저장해야 한다")
    void addCredits_AddsBalanceAndSavesLog() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);

        creditService.addCredits(requestUser, 5, CreditTransactionType.REFERRAL_BONUS);

        assertThat(loginUser.getBallBalance()).isEqualTo(5);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(5);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.REFERRAL_BONUS);
    }

    @Test
    @DisplayName("일일 획득 제한을 초과하면 남은 한도만큼만 지급해야 한다")
    void addCredits_CapsAtDailyLimit() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(creditTransactionRepository.sumPositiveAmountByUserAndCreatedAtBetween(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(19);

        creditService.addCredits(requestUser, 5, CreditTransactionType.STYLE_POST);

        assertThat(loginUser.getBallBalance()).isEqualTo(1);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.STYLE_POST);
    }

    @Test
    @DisplayName("대체 실 정보는 도안별 최초 조회 시에만 크레딧을 차감해야 한다")
    void spendCreditsForFirstAlternativeView_ChargesOncePerPattern() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(unlockRepository.existsByUser_IdAndPatternIdAndType(1L, 100L, UnlockType.YARN_INFO)).thenReturn(false);

        creditService.spendCreditsForFirstAlternativeView(requestUser, 100L);

        assertThat(loginUser.getBallBalance()).isEqualTo(-5);
        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(-5);
        assertThat(txCaptor.getValue().getType()).isEqualTo(CreditTransactionType.ALT_YARN_VIEW);

        ArgumentCaptor<Unlock> unlockCaptor = ArgumentCaptor.forClass(Unlock.class);
        verify(unlockRepository).save(unlockCaptor.capture());
        assertThat(unlockCaptor.getValue().getPatternId()).isEqualTo(100L);
        assertThat(unlockCaptor.getValue().getType()).isEqualTo(UnlockType.YARN_INFO);
    }

    @Test
    @DisplayName("이미 해금된 도안의 대체 실 조회는 크레딧을 차감하지 않아야 한다")
    void spendCreditsForFirstAlternativeView_SkipsWhenAlreadyUnlocked() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(unlockRepository.existsByUser_IdAndPatternIdAndType(1L, 100L, UnlockType.YARN_INFO)).thenReturn(true);

        creditService.spendCreditsForFirstAlternativeView(requestUser, 100L);

        assertThat(loginUser.getBallBalance()).isEqualTo(0);
        org.mockito.Mockito.verifyNoInteractions(creditTransactionRepository);
        org.mockito.Mockito.verify(unlockRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void setTransactionId(CreditTransaction transaction, Long id) {
        try {
            java.lang.reflect.Field idField = CreditTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
