package com.ufo.ufo.domain.pattern.api;

import com.ufo.ufo.domain.pattern.application.PatternService;
import com.ufo.ufo.domain.pattern.application.PatternPurchaseService;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeDeleteResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativesResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.validation.ValidPatternCategory;
import com.ufo.ufo.domain.pattern.validation.ValidPatternSort;
import com.ufo.ufo.domain.pattern.validation.ValidPatternSubCategory;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.PatternScrapResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

    private final PatternService patternService;
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
            @Min(value = 1, message = "page는 1 이상이어야 합니다.")
            Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.getPatterns(user, category, subCategory, sort, page)));
    }

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<PatternItemsResponse>> getRecommendedPatterns(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(patternService.getRecommendedPatterns(user)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PatternListResponse>> searchPatterns(
            @LoginUser User user,
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "page") Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.searchPatterns(user, keyword, page)));
    }

    @GetMapping("/{patternId}")
    public ResponseEntity<ApiResponse<PatternDetailResponse>> getPatternDetail(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.getPatternDetail(user, patternId)));
    }

    @GetMapping("/{patternId}/alternatives")
    public ResponseEntity<ApiResponse<PatternAlternativesResponse>> getAlternatives(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.getAlternatives(user, patternId)));
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
    public ResponseEntity<ApiResponse<PatternAlternativesResponse.Item>> createAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @Valid @RequestBody CreateAlternativeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.createAlternative(user, patternId, request)));
    }

    @PatchMapping("/{patternId}/alternatives/{altId}")
    public ResponseEntity<ApiResponse<PatternAlternativesResponse.Item>> updateAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @PathVariable("altId") Long altId,
            @Valid @RequestBody UpdateAlternativeYarnRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(patternService.updateAlternative(user, patternId, altId, request)));
    }

    @DeleteMapping("/{patternId}/alternatives/{altId}")
    public ResponseEntity<ApiResponse<PatternAlternativeDeleteResponse>> deleteAlternative(
            @LoginUser User user,
            @PathVariable("patternId") Long patternId,
            @PathVariable("altId") Long altId
    ) {
        patternService.deleteAlternative(user, patternId, altId);
        return ResponseEntity.ok(ApiResponse.success(PatternAlternativeDeleteResponse.from(user.getId(), altId)));
    }
}
