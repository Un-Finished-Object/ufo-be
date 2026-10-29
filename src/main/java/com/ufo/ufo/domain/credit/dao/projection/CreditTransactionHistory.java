package com.ufo.ufo.domain.credit.dao.projection;

import java.time.LocalDateTime;

public interface CreditTransactionHistory {

    Long getId();

    int getAmount();

    int getBalanceAfter();

    String getReason();

    LocalDateTime getCreatedAt();
}
