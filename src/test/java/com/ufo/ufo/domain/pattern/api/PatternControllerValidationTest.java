package com.ufo.ufo.domain.pattern.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.domain.pattern.application.PatternPurchaseService;
import com.ufo.ufo.domain.pattern.application.PatternService;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.exception.PatternSubCategoryNotAllowedException;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = PatternController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("도안 컨트롤러 파라미터 검증 테스트")
class PatternControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatternService patternService;

    @MockitoBean
    private PatternPurchaseService patternPurchaseService;

    @MockitoBean
    private ScrapService scrapService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("유효한 파라미터 요청이면 200을 반환하고 서비스를 호출한다")
    void getPatterns_ValidParams_ReturnsOk() throws Exception {
        when(patternService.getPatterns(any(), anyString(), any(), anyString(), anyInt()))
                .thenReturn(new PatternListResponse(List.of(), 1, 0));

        mockMvc.perform(get("/v1/patterns")
                        .param("category", "all")
                        .param("sort", "news")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(patternService).getPatterns(any(), anyString(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("category가 허용값이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void getPatterns_InvalidCategory_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/patterns")
                        .param("category", "clothing")
                        .param("sort", "news")
                        .param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("category는 all, apparel, bags, accessories, others 중 하나여야 합니다."));

        verify(patternService, never()).getPatterns(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sort가 허용값이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void getPatterns_InvalidSort_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/patterns")
                        .param("category", "all")
                        .param("sort", "latest")
                        .param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("sort는 news, views, scraps 중 하나여야 합니다."));

        verify(patternService, never()).getPatterns(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("page가 1 미만이면 400을 반환하고 서비스를 호출하지 않는다")
    void getPatterns_InvalidPage_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/patterns")
                        .param("category", "all")
                        .param("sort", "news")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page는 1 이상이어야 합니다."));

        verify(patternService, never()).getPatterns(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("subCategory가 허용값이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void getPatterns_InvalidSubCategory_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/patterns")
                        .param("category", "apparel")
                        .param("subCategory", "shirt")
                        .param("sort", "news")
                        .param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("subCategory는 outer, sweater, vest, dress, others 중 하나여야 합니다."));

        verify(patternService, never()).getPatterns(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("category가 apparel이 아닌데 subCategory가 있으면 400을 반환한다")
    void getPatterns_SubCategoryProvidedForNonApparel_ReturnsBadRequest() throws Exception {
        when(patternService.getPatterns(any(), eq("all"), eq("sweater"), eq("news"), eq(1)))
                .thenThrow(new PatternSubCategoryNotAllowedException());

        mockMvc.perform(get("/v1/patterns")
                        .param("category", "all")
                        .param("subCategory", "sweater")
                        .param("sort", "news")
                        .param("page", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("subCategory는 category가 apparel일 때만 사용할 수 있습니다."));

        verify(patternService).getPatterns(any(), eq("all"), eq("sweater"), eq("news"), eq(1));
    }
}
