package com.ufo.ufo.domain.credit.application;

import com.ufo.ufo.domain.credit.dao.CreditTransactionRepository;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.credit.exception.InsufficientCreditException;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionItemResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditWalletResponse;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditService {

    private static final int TRANSACTION_PAGE_SIZE = 20;

    private final UserService userService;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UnlockRepository unlockRepository;

    public CreditWalletResponse getWallet(User user) {
        User loginUser = userService.getUserById(user.getId());
        return new CreditWalletResponse(loginUser.getBallBalance());
    }

    public CreditTransactionsResponse getTransactions(User user, String type, String reason, Integer page) {
        User loginUser = userService.getUserById(user.getId());
        int pageNumber = normalizePage(page);
        List<CreditTransaction> transactions = creditTransactionRepository.findAll(
                        createTransactionFilter(loginUser.getId(), type, reason),
                        PageRequest.of(pageNumber - 1, TRANSACTION_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .getContent();

        List<CreditTransactionItemResponse> items = toTransactionItems(transactions, loginUser.getBallBalance());
        return new CreditTransactionsResponse(items, pageNumber);
    }

    public CreditRulesResponse getRules() {
        return new CreditRulesResponse(
                CreditPolicy.DAILY_MAX_EARN_BALLS,
                CreditPolicy.earnRules(),
                CreditPolicy.spendRules()
        );
    }

    @Transactional
    public void purchaseUnlock(User user, Long patternId, UnlockType unlockType) {
        User loginUser = userService.getUserById(user.getId());
        if (isUnlocked(loginUser.getId(), patternId, unlockType)) {
            return;
        }
        int cost = resolveUnlockCost(unlockType);
        validateEnoughBalance(loginUser, cost);
        addCredits(loginUser, -cost, resolveTransactionType(unlockType));
        unlockRepository.save(Unlock.builder()
                .user(loginUser)
                .patternId(patternId)
                .type(unlockType)
                .build());
    }

    public boolean isUnlocked(Long userId, Long patternId, UnlockType unlockType) {
        return unlockRepository.existsByUser_IdAndPatternIdAndType(userId, patternId, unlockType);
    }

    @Transactional
    public void addCredits(User user, int amount, CreditTransactionType type) {
        User loginUser = userService.getUserById(user.getId());
        int appliedAmount = resolveAppliedAmount(loginUser, amount, type);
        if (appliedAmount == 0) {
            return;
        }
        loginUser.addCredits(appliedAmount);
        saveTransaction(loginUser, appliedAmount, type);
    }

    private int resolveAppliedAmount(User user, int amount, CreditTransactionType type) {
        if (!requiresDailyCap(amount, type)) {
            return amount;
        }
        int remaining = calculateRemainingDailyEarnLimit(user);
        return capByRemainingLimit(amount, remaining);
    }

    private void saveTransaction(User user, int amount, CreditTransactionType type) {
        creditTransactionRepository.save(CreditTransaction.builder()
                .user(user)
                .amount(amount)
                .type(type)
                .build());
    }

    private boolean requiresDailyCap(int amount, CreditTransactionType type) {
        return amount > 0 && !type.isDailyLimitExempt();
    }

    private int calculateRemainingDailyEarnLimit(User user) {
        int earnedToday = getTodayEarnedAmount(user.getId());
        return Math.max(0, CreditPolicy.DAILY_MAX_EARN_BALLS - earnedToday);
    }

    private int getTodayEarnedAmount(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        return creditTransactionRepository.sumPositiveAmountByUserAndCreatedAtBetween(userId, from, to);
    }

    private int capByRemainingLimit(int amount, int remaining) {
        return Math.min(amount, remaining);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private Specification<CreditTransaction> createTransactionFilter(Long userId, String type, String reason) {
        Specification<CreditTransaction> specification = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);

        if (StringUtils.hasText(type)) {
            String upperType = type.toUpperCase(Locale.ROOT);
            if ("EARN".equals(upperType)) {
                specification = specification.and((root, query, cb) -> cb.greaterThan(root.get("amount"), 0));
            } else if ("SPEND".equals(upperType)) {
                specification = specification.and((root, query, cb) -> cb.lessThan(root.get("amount"), 0));
            }
        }

        if (StringUtils.hasText(reason)) {
            String upperReason = reason.toUpperCase(Locale.ROOT);
            specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), CreditTransactionType.valueOf(upperReason)));
        }
        return specification;
    }

    private List<CreditTransactionItemResponse> toTransactionItems(List<CreditTransaction> transactions, int currentBalance) {
        int balanceAfter = currentBalance;
        List<CreditTransactionItemResponse> items = new java.util.ArrayList<>(transactions.size());
        for (CreditTransaction transaction : transactions) {
            items.add(toTransactionItem(transaction, balanceAfter));
            balanceAfter -= transaction.getAmount();
        }
        return items;
    }

    private CreditTransactionItemResponse toTransactionItem(CreditTransaction transaction, int balanceAfter) {
        return new CreditTransactionItemResponse(
                "ct_" + transaction.getId(),
                toFlowType(transaction),
                Math.abs(transaction.getAmount()),
                balanceAfter,
                transaction.getType().name(),
                transaction.getCreatedAt()
        );
    }

    private String toFlowType(CreditTransaction transaction) {
        return transaction.getAmount() >= 0 ? "EARN" : "SPEND";
    }

    private int resolveUnlockCost(UnlockType unlockType) {
        return switch (unlockType) {
            case CHAT -> CreditPolicy.CHATROOM_ENTRY_COST_BALLS;
            case YARN_INFO -> CreditPolicy.ALT_YARN_VIEW_COST_BALLS;
        };
    }

    private CreditTransactionType resolveTransactionType(UnlockType unlockType) {
        return switch (unlockType) {
            case CHAT -> CreditTransactionType.CHATROOM_ENTRY;
            case YARN_INFO -> CreditTransactionType.ALT_YARN_VIEW;
        };
    }

    private void validateEnoughBalance(User user, int cost) {
        if (user.getBallBalance() < cost) {
            throw new InsufficientCreditException();
        }
    }
}
