package com.ufo.ufo.domain.credit.application;

import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.dao.CreditTransactionRepository;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.credit.exception.InsufficientCreditException;
import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditWalletResponse;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import java.util.Optional;
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
        assertThat(response.earnRules())
                .extracting(CreditRulesResponse.Rule::key, CreditRulesResponse.Rule::amount,
                        CreditRulesResponse.Rule::dailyLimitExempt)
                .contains(
                        tuple("SIGNUP_BONUS", CreditPolicy.SIGNUP_BONUS_BALLS, true),
                        tuple("ATTENDANCE_DAILY", CreditPolicy.ATTENDANCE_DAILY_BALLS, false),
                        tuple("REFERRAL_BONUS", CreditPolicy.REFERRAL_BONUS_BALLS, true)
                );
        assertThat(response.spendRules()).isNotEmpty();
    }

    @Test
    @DisplayName("구매는 잠근 사용자에게서 비용을 차감하고 거래와 해금을 저장한다")
    void purchaseUnlock_LocksUserAndDeductsCost() {
        User user = UserFixture.createUserWithId(1L);
        user.addCredits(10);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);

        assertThat(creditService.purchaseUnlock(user, 10L, UnlockType.YARN_INFO)).isTrue();

        assertThat(user.getBallBalance()).isZero();
        verify(unlockRepository).save(org.mockito.ArgumentMatchers.any(Unlock.class));
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(-10);
    }

    @Test
    @DisplayName("이미 해금한 구매는 잔액이 없어도 다시 차감하지 않고 중복 결과를 반환한다")
    void purchaseUnlock_AlreadyUnlocked_DoesNotCharge() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);
        when(unlockRepository.findByUser_IdAndPatternIdAndType(1L, 10L, UnlockType.YARN_INFO))
                .thenReturn(Optional.of(Unlock.builder().user(user).patternId(10L).type(UnlockType.YARN_INFO).build()));

        assertThat(creditService.purchaseUnlock(user, 10L, UnlockType.YARN_INFO)).isFalse();

        assertThat(user.getBallBalance()).isZero();
        verifyNoInteractions(creditTransactionRepository);
    }

    @Test
    @DisplayName("잔액 부족 구매는 거래를 저장하지 않고 잔액 부족 예외로 거절한다")
    void purchaseUnlock_InsufficientBalance_DoesNotSaveTransaction() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);

        assertThatThrownBy(() -> creditService.purchaseUnlock(user, 10L, UnlockType.YARN_INFO))
                .isInstanceOf(InsufficientCreditException.class);

        verifyNoInteractions(creditTransactionRepository);
        assertThat(user.getBallBalance()).isZero();
    }

    @Test
    @DisplayName("크레딧 추가는 사용자 잔액을 올리고 로그를 저장해야 한다")
    void addCredits_AddsBalanceAndSavesLog() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(loginUser);

        creditService.addCredits(requestUser, 5, CreditTransactionType.REFERRAL_BONUS);

        assertThat(loginUser.getBallBalance()).isEqualTo(5);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(5);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.REFERRAL_BONUS);
    }

    @Test
    @DisplayName("친구 초대 보상은 잔액을 원자적으로 증가시키고 로그를 저장해야 한다")
    void awardReferralBonus_IncrementsBalanceAndSavesLog() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(user);

        creditService.awardReferralBonus(user, 150);

        assertThat(user.getBallBalance()).isEqualTo(150);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getAmount()).isEqualTo(150);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.REFERRAL_BONUS);
    }

    @Test
    @DisplayName("일일 획득 제한을 초과하면 남은 한도만큼만 지급해야 한다")
    void addCredits_CapsAtDailyLimit() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(loginUser);
        when(creditTransactionRepository.findDailyEarningsForUpdate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(CreditTransaction.builder()
                        .user(loginUser).amount(19).type(CreditTransactionType.ATTENDANCE_DAILY).build()));

        creditService.addCredits(requestUser, 5, CreditTransactionType.ATTENDANCE_DAILY);

        assertThat(loginUser.getBallBalance()).isEqualTo(1);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.ATTENDANCE_DAILY);
    }

    @Test
    @DisplayName("회원가입 보상은 일일 획득 제한과 무관하게 전체 금액을 지급해야 한다")
    void addCredits_WithSignupBonus_IgnoresDailyLimit() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserByIdForUpdate(1L)).thenReturn(loginUser);

        creditService.addCredits(requestUser, CreditPolicy.SIGNUP_BONUS_BALLS, CreditTransactionType.SIGNUP_BONUS);

        assertThat(loginUser.getBallBalance()).isEqualTo(CreditPolicy.SIGNUP_BONUS_BALLS);
        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(CreditPolicy.SIGNUP_BONUS_BALLS);
        assertThat(captor.getValue().getType()).isEqualTo(CreditTransactionType.SIGNUP_BONUS);
    }

    private void setTransactionId(CreditTransaction transaction, Long id) {
        try {
            Field idField = CreditTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
