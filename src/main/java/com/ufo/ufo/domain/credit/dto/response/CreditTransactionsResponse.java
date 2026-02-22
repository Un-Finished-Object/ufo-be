package com.ufo.ufo.domain.credit.dto.response;

import java.util.List;

public record CreditTransactionsResponse(
        List<CreditTransactionItemResponse> items,
        int page
) {
}
