package com.ufo.ufo.domain.referral.api;

import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.referral.dto.request.RegisterReferralCodeRequest;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeRegistrationResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReferralCodeRegistrationResponse>> registerReferralCode(
            @LoginUser User user,
            @RequestBody @Valid RegisterReferralCodeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(referralService.registerReferralCode(user, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> getReferralCode(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(referralService.getReferralCode(user)));
    }
}
