package com.ufo.ufo.domain.credit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ufo.ufo.domain.credit.dao.CreditTransactionRepository;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionItemResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

abstract class CreditTransactionHistoryTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private CreditService creditService;
    @Autowired
    private CreditTransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private ImageService imageService;

    @ParameterizedTest
    @CsvSource({"SPEND,", ",CHATROOM_ENTRY", "SPEND,CHATROOM_ENTRY", "spend,chatroom_entry"})
    @DisplayName("지출 유형이나 사유로 조회해도 거래 직후 잔액을 반환해야 한다")
    void getTransactions_WithSpendFilters_ReturnsOriginalBalance(String type, String reason) {
        User user = createUser(150);
        createStandardHistory(user);

        CreditTransactionsResponse response = creditService.getTransactions(user, type, reason, 1);

        assertThat(response.items()).hasSize(1);
        CreditTransactionItemResponse transaction = response.items().getFirst();
        assertThat(transaction.type()).isEqualTo("SPEND");
        assertThat(transaction.amount()).isEqualTo(10);
        assertThat(transaction.reason()).isEqualTo("CHATROOM_ENTRY");
        assertThat(transaction.balanceAfter()).isEqualTo(140);
        assertThat(transaction.createdAt()).isEqualTo(START.plusSeconds(1));
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    @DisplayName("적립 내역만 조회해도 중간 지출을 반영한 거래 후 잔액을 반환해야 한다")
    void getTransactions_WithEarnFilter_IncludesInterveningSpend() {
        User user = createUser(150);
        createStandardHistory(user);

        CreditTransactionsResponse response = creditService.getTransactions(user, "EARN", null, 1);

        assertThat(response.items()).extracting(CreditTransactionItemResponse::balanceAfter)
                .containsExactly(150, 150);
        assertThat(response.items()).extracting(CreditTransactionItemResponse::amount).containsExactly(10, 150);
    }

    @Test
    @DisplayName("두 번째 페이지에서도 첫 페이지의 거래를 반영한 잔액을 반환해야 한다")
    void getTransactions_WithSecondPage_ReturnsOriginalBalances() {
        User user = createUser(128);
        createLongHistory(user);

        CreditTransactionsResponse first = creditService.getTransactions(user, null, null, 1);
        CreditTransactionsResponse second = creditService.getTransactions(user, null, null, 2);

        assertThat(first.items()).hasSize(20);
        assertThat(first.items().getFirst().balanceAfter()).isEqualTo(128);
        assertThat(first.items().getLast().balanceAfter()).isEqualTo(147);
        assertThat(second.items()).extracting(CreditTransactionItemResponse::balanceAfter)
                .containsExactly(148, 149, 150);
        assertThat(second.page()).isEqualTo(2);
    }

    @Test
    @DisplayName("필터를 적용한 두 번째 페이지에서도 거래 직후 잔액을 반환해야 한다")
    void getTransactions_WithFilteredSecondPage_ReturnsOriginalBalances() {
        User user = createUser(128);
        createLongHistory(user);

        CreditTransactionsResponse response = creditService.getTransactions(user, "SPEND", "CHATROOM_ENTRY", 2);

        assertThat(response.items()).extracting(CreditTransactionItemResponse::balanceAfter)
                .containsExactly(148, 149);
        assertThat(response.items()).extracting(CreditTransactionItemResponse::type)
                .containsOnly("SPEND");
    }

    @Test
    @DisplayName("생성 시각이 같으면 ID 내림차순으로 정렬하고 페이지 사이에 거래가 겹치지 않아야 한다")
    void getTransactions_WithSameCreatedAt_OrdersPagesByDescendingId() {
        User user = createUser(128);
        List<Long> ids = new ArrayList<>();
        ids.add(saveTransaction(user, 150, CreditTransactionType.SIGNUP_BONUS, START));
        for (int index = 0; index < 22; index++) {
            ids.add(saveTransaction(user, -1, CreditTransactionType.CHATROOM_ENTRY, START));
        }
        entityManager.clear();

        CreditTransactionsResponse first = creditService.getTransactions(user, null, null, 1);
        CreditTransactionsResponse second = creditService.getTransactions(user, null, null, 2);
        List<String> actualIds = new ArrayList<>();
        first.items().forEach(transaction -> actualIds.add(transaction.id()));
        second.items().forEach(transaction -> actualIds.add(transaction.id()));

        assertThat(actualIds).containsExactlyElementsOf(ids.reversed().stream().map(id -> "ct_" + id).toList());
        assertThat(second.items()).extracting(CreditTransactionItemResponse::balanceAfter)
                .containsExactly(148, 149, 150);
    }

    @Test
    @DisplayName("다른 사용자의 거래는 목록과 잔액 계산에서 제외해야 한다")
    void getTransactions_WithOtherUsersTransactions_ReturnsOnlyOwnHistory() {
        User user = createUser(140);
        saveTransaction(user, 150, CreditTransactionType.SIGNUP_BONUS, START);
        saveTransaction(user, -10, CreditTransactionType.CHATROOM_ENTRY, START.plusSeconds(2));
        User other = createUser(900);
        saveTransaction(other, 900, CreditTransactionType.SIGNUP_BONUS, START.plusSeconds(1));
        entityManager.clear();

        CreditTransactionsResponse response = creditService.getTransactions(user, null, null, 1);

        assertThat(response.items()).extracting(CreditTransactionItemResponse::balanceAfter)
                .containsExactly(140, 150);
        assertThat(response.items()).extracting(CreditTransactionItemResponse::amount).containsExactly(10, 150);
    }

    @Test
    @DisplayName("거래 기록 이전의 잔액이 있어도 기존 거래 후 잔액을 유지해야 한다")
    void getTransactions_WithOpeningBalance_PreservesExistingBalance() {
        User user = createUser(160);
        createStandardHistory(user);

        CreditTransactionsResponse response = creditService.getTransactions(user, "SPEND", null, 1);

        assertThat(response.items().getFirst().balanceAfter()).isEqualTo(150);
    }

    @Test
    @DisplayName("사용자 객체의 잔액이 오래됐어도 DB의 거래와 잔액을 함께 읽어야 한다")
    void getTransactions_WithStaleManagedUser_UsesDatabaseBalance() {
        User user = createUser(150);
        createStandardHistory(user);
        User stale = userRepository.findById(user.getId()).orElseThrow();
        saveTransaction(stale, 10, CreditTransactionType.REFERRAL_BONUS, START.plusSeconds(3));
        jdbcTemplate.update("update users set ball_balance = 160 where user_id = ?", user.getId());
        assertThat(stale.getBallBalance()).isEqualTo(150);

        CreditTransactionsResponse response = creditService.getTransactions(stale, "SPEND", null, 1);

        assertThat(response.items().getFirst().balanceAfter()).isEqualTo(140);
    }

    @Test
    @DisplayName("추가 적립 후에도 이전 구매의 거래 후 잔액은 같아야 한다")
    void getTransactions_AfterAdditionalEarnings_PreservesPreviousBalance() {
        User user = createUser(0);
        creditService.addCredits(user, 150, CreditTransactionType.SIGNUP_BONUS);
        creditService.purchaseUnlock(user, 1L, UnlockType.CHAT);
        CreditTransactionsResponse before = creditService.getTransactions(user, "SPEND", null, 1);

        creditService.addCredits(user, 10, CreditTransactionType.ATTENDANCE_DAILY);
        CreditTransactionsResponse after = creditService.getTransactions(user, "SPEND", null, 1);

        assertThat(before.items().getFirst().balanceAfter()).isEqualTo(140);
        assertThat(after.items().getFirst().balanceAfter()).isEqualTo(140);
        assertThat(after.items().getFirst().id()).isEqualTo(before.items().getFirst().id());
    }

    @Test
    @DisplayName("거래가 없거나 마지막 페이지를 넘으면 빈 목록을 반환해야 한다")
    void getTransactions_WithNoMatchingPage_ReturnsEmptyItems() {
        User user = createUser(150);
        assertThat(creditService.getTransactions(user, null, null, 1).items()).isEmpty();
        createStandardHistory(user);

        assertThat(creditService.getTransactions(user, null, null, 2).items()).isEmpty();
        assertThat(creditService.getTransactions(user, "SPEND", "SIGNUP_BONUS", 1).items()).isEmpty();
    }

    private User createUser(int balance) {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        User user = UserFixture.createUser(identifier + "@example.com", Role.ROLE_USER);
        user.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
        user.addCredits(balance);
        return userRepository.saveAndFlush(user);
    }

    private void createStandardHistory(User user) {
        saveTransaction(user, 150, CreditTransactionType.SIGNUP_BONUS, START);
        saveTransaction(user, -10, CreditTransactionType.CHATROOM_ENTRY, START.plusSeconds(1));
        saveTransaction(user, 10, CreditTransactionType.ATTENDANCE_DAILY, START.plusSeconds(2));
        entityManager.clear();
    }

    private void createLongHistory(User user) {
        saveTransaction(user, 150, CreditTransactionType.SIGNUP_BONUS, START);
        for (int index = 1; index <= 22; index++) {
            saveTransaction(user, -1, CreditTransactionType.CHATROOM_ENTRY, START.plusSeconds(index));
        }
        entityManager.clear();
    }

    private Long saveTransaction(User user, int amount, CreditTransactionType type, LocalDateTime createdAt) {
        CreditTransaction transaction = transactionRepository.saveAndFlush(CreditTransaction.builder()
                .user(user)
                .amount(amount)
                .type(type)
                .build());
        jdbcTemplate.update("update credit_logs set created_at = ? where log_id = ?",
                Timestamp.valueOf(createdAt), transaction.getId());
        return transaction.getId();
    }
}
