package com.ufo.ufo.domain.pattern.api;

import com.ufo.ufo.domain.pattern.application.YarnQueryService;
import com.ufo.ufo.domain.pattern.dto.response.YarnResponse;
import com.ufo.ufo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/yarns")
@RequiredArgsConstructor
public class YarnController {

    private final YarnQueryService yarnQueryService;

    @GetMapping("/{yarnId}")
    public ResponseEntity<ApiResponse<YarnResponse>> getYarnDetail(@PathVariable("yarnId") Long yarnId) {
        return ResponseEntity.ok(ApiResponse.success(yarnQueryService.getYarnDetail(yarnId)));
    }
}
