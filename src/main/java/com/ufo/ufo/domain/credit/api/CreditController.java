package com.ufo.ufo.domain.credit.api;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditWalletResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<CreditWalletResponse>> getWallet(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(creditService.getWallet(user)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<CreditTransactionsResponse>> getTransactions(
            @LoginUser User user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(creditService.getTransactions(user, type, reason, page)));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<CreditRulesResponse>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(creditService.getRules()));
    }
}
