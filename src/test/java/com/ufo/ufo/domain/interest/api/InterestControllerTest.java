package com.ufo.ufo.domain.interest.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.interest.dto.response.InterestKeywordsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("관심사 컨트롤러 테스트")
class InterestControllerTest {

    @Mock
    private InterestService interestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InterestController controller = new InterestController(interestService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("관심사 키워드 목록 API는 data에 키워드 배열을 반환해야 한다")
    void getInterestKeywords_ReturnsKeywords() throws Exception {
        when(interestService.getInterestKeywords())
                .thenReturn(new InterestKeywordsResponse(List.of("빈티지", "캐주얼")));

        mockMvc.perform(get("/v1/interest/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords[0]").value("빈티지"))
                .andExpect(jsonPath("$.data.keywords[1]").value("캐주얼"))
                .andExpect(jsonPath("$.error").isEmpty());
    }
}
