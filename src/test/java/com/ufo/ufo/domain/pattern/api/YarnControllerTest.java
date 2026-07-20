package com.ufo.ufo.domain.pattern.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.application.AlternativeYarnQueryService;
import com.ufo.ufo.domain.pattern.application.YarnQueryService;
import com.ufo.ufo.domain.pattern.dto.response.YarnResponse;
import com.ufo.ufo.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("실 컨트롤러 테스트")
class YarnControllerTest {

    @Mock
    private YarnQueryService yarnQueryService;

    @Mock
    private AlternativeYarnQueryService alternativeYarnQueryService;

    @InjectMocks
    private YarnController yarnController;

    @Test
    @DisplayName("실 상세 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getYarnDetail_ReturnsServiceResponse() {
        YarnResponse serviceResponse = new YarnResponse(
                1L,
                "앨모",
                100,
                17000,
                "알파카 90%, 나일론 10%",
                "솜솜뜨개",
                180
        );
        when(yarnQueryService.getYarnDetail(1L)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<YarnResponse>> response = yarnController.getYarnDetail(1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().yarnId()).isEqualTo(1L);
        assertThat(response.getBody().data().yarnName()).isEqualTo("앨모");
        assertThat(response.getBody().data().component()).isEqualTo("알파카 90%, 나일론 10%");
        verify(yarnQueryService).getYarnDetail(1L);
    }
}
