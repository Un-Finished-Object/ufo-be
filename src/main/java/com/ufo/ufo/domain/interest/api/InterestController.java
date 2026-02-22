package com.ufo.ufo.domain.interest.api;

import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.interest.dto.response.InterestKeywordsResponse;
import com.ufo.ufo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/interest")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<InterestKeywordsResponse>> getInterestKeywords() {
        return ResponseEntity.ok(ApiResponse.success(interestService.getInterestKeywords()));
    }
}
