package com.ufo.ufo.domain.pattern.api;

import com.ufo.ufo.domain.pattern.application.PatternAlternativeService;
import com.ufo.ufo.domain.pattern.application.PatternQueryService;
import com.ufo.ufo.domain.pattern.application.PatternRecommendationService;
import com.ufo.ufo.domain.pattern.application.PatternViewService;
import com.ufo.ufo.domain.pattern.application.PatternPurchaseService;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeDeleteResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternViewCountResponse;
import com.ufo.ufo.domain.pattern.validation.ValidPatternCategory;
import com.ufo.ufo.domain.pattern.validation.ValidPatternSort;
import com.ufo.ufo.domain.pattern.validation.ValidPatternSubCategory;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.PatternScrapResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import com.ufo.ufo.global.validation.ValidPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/patterns")
@RequiredArgsConstructor
@Validated
public class PatternController {

    private final PatternQueryService patternQueryService;
    private final PatternRecommendationService patternRecommendationService;
    private final PatternViewService patternViewService;
    private final PatternAlternativeService patternAlternativeService;
    private final PatternPurchaseService patternPurchaseService;
    private final ScrapService scrapService;

    @GetMapping
    public ResponseEntity<ApiResponse<PatternListResponse>> getPatterns(
            @LoginUser User user,
            @RequestParam(name = "category")
            @ValidPatternCategory
            String category,
            @RequestParam(name = "subCategory", required = false)
            @ValidPatternSubCategory
            String subCategory,
            @RequestParam(name = "sort")
            @ValidPatternSort
            String sort,
            @RequestParam(name = "page")
            @ValidPage
            Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternQueryService.getPatterns(user, category, subCategory, sort, page)));
    }

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<PatternItemsResponse>> getRecommendedPatterns(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(patternRecommendationService.getRecommendedPatterns(user)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PatternListResponse>> searchPatterns(
            @LoginUser User user,
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "page")
            @ValidPage
            Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternQueryService.searchPatterns(user, keyword, page)));
    }

    @GetMapping("/{patternId}")
    public ResponseEntity<ApiResponse<PatternDetailResponse>> getPatternDetail(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternQueryService.getPatternDetail(user, patternId)));
    }

    @PostMapping("/{patternId}/views")
    public ResponseEntity<ApiResponse<PatternViewCountResponse>> increaseViewCount(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternViewService.increaseViewCount(user, patternId)));
    }

    @PostMapping("/{patternId}/purchase")
    public ResponseEntity<ApiResponse<PatternPurchaseResponse>> purchase(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @Valid @RequestBody PatternPurchaseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternPurchaseService.purchase(user, patternId, request)));
    }

    @GetMapping("/{patternId}/purchase")
    public ResponseEntity<ApiResponse<PatternPurchaseStatusResponse>> getPurchaseStatus(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternPurchaseService.getStatus(user, patternId)));
    }

    @PostMapping("/{patternId}/scrap")
    public ResponseEntity<ApiResponse<PatternScrapResponse>> addPatternScrap(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(scrapService.addPatternScrap(user, patternId)));
    }

    @DeleteMapping("/{patternId}/scrap")
    public ResponseEntity<ApiResponse<PatternScrapResponse>> removePatternScrap(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(scrapService.removePatternScrap(user, patternId)));
    }

    @PostMapping("/{patternId}/alternatives")
    public ResponseEntity<ApiResponse<PatternAlternativeResponse>> createAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @Valid @RequestBody CreateAlternativeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternAlternativeService.createAlternative(user, patternId, request)));
    }

    @PatchMapping("/{patternId}/alternatives/{altId}")
    public ResponseEntity<ApiResponse<PatternAlternativeResponse>> updateAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @PathVariable("altId") Long altId,
            @Valid @RequestBody UpdateAlternativeYarnRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternAlternativeService.updateAlternative(user, patternId, altId, request)));
    }

    @DeleteMapping("/{patternId}/alternatives/{altId}")
    public ResponseEntity<ApiResponse<PatternAlternativeDeleteResponse>> deleteAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @PathVariable("altId") Long altId
    ) {
        patternAlternativeService.deleteAlternative(user, patternId, altId);
        return ResponseEntity.ok(ApiResponse.success(PatternAlternativeDeleteResponse.from(user.getId(), altId)));
    }
}

