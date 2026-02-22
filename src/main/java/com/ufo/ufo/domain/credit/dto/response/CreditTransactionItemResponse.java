package com.ufo.ufo.domain.credit.dto.response;

import java.time.LocalDateTime;

public record CreditTransactionItemResponse(
        String id,
        String type,
        int amount,
        int balanceAfter,
        String reason,
        LocalDateTime createdAt
) {
}
