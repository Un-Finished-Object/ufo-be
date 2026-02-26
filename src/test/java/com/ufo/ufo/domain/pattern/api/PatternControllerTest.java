package com.ufo.ufo.domain.pattern.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.application.PatternService;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeDeleteResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativesResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternMyResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternStatsResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("도안 컨트롤러 테스트")
class PatternControllerTest {

    @Mock
    private PatternService patternService;

    @InjectMocks
    private PatternController patternController;

    @Test
    @DisplayName("도안 목록 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getPatterns_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.getPatterns(user, "clothing", "sweater", "news", 1))
                .thenReturn(new PatternListResponse(List.of(), 1));

        ResponseEntity<ApiResponse<PatternListResponse>> response =
                patternController.getPatterns(user, "clothing", "sweater", "news", 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().page()).isEqualTo(1);
        verify(patternService).getPatterns(user, "clothing", "sweater", "news", 1);
    }

    @Test
    @DisplayName("도안 상세 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getPatternDetail_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.getPatternDetail(user, 10L))
                .thenReturn(new PatternDetailResponse(
                        10L,
                        "t",
                        java.util.List.of("img"),
                        "a",
                        new PatternDetailResponse.Meta("c", "s", null, null, null, null, null, null),
                        new PatternStatsResponse(1, 2L),
                        new PatternMyResponse(false)
                ));

        ResponseEntity<ApiResponse<PatternDetailResponse>> response = patternController.getPatternDetail(user, 10L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().id()).isEqualTo(10L);
        verify(patternService).getPatternDetail(user, 10L);
    }

    @Test
    @DisplayName("대체 실 등록 API는 서비스 응답을 data에 담아 반환해야 한다")
    void createAlternative_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        CreateAlternativeRequest request = new CreateAlternativeRequest("n", "./yarns/1.png", 100, 10000, "", "");
        when(patternService.createAlternative(user, 10L, request))
                .thenReturn(new PatternAlternativesResponse.Item(1L, "n", "./yarns/1.png", 10000, 100, "", "", "admin"));

        ResponseEntity<ApiResponse<PatternAlternativesResponse.Item>> response =
                patternController.createAlternative(user, 10L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().altId()).isEqualTo(1L);
        verify(patternService).createAlternative(user, 10L, request);
    }

    @Test
    @DisplayName("대체 실 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getAlternatives_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.getAlternatives(user, 10L))
                .thenReturn(new PatternAlternativesResponse(List.of()));

        ResponseEntity<ApiResponse<PatternAlternativesResponse>> response = patternController.getAlternatives(user, 10L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().items()).isEmpty();
        verify(patternService).getAlternatives(user, 10L);
    }

    @Test
    @DisplayName("추천 도안 조회 API는 서비스 응답을 data.items에 담아 반환해야 한다")
    void getRecommendedPatterns_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.getRecommendedPatterns(user))
                .thenReturn(new PatternItemsResponse(List.of()));

        ResponseEntity<ApiResponse<PatternItemsResponse>> response = patternController.getRecommendedPatterns(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().items()).isEmpty();
        verify(patternService).getRecommendedPatterns(user);
    }

    @Test
    @DisplayName("도안 검색 API는 서비스 응답을 data에 담아 반환해야 한다")
    void searchPatterns_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.searchPatterns(user, "니트", 2))
                .thenReturn(new PatternListResponse(List.of(), 2));

        ResponseEntity<ApiResponse<PatternListResponse>> response = patternController.searchPatterns(user, "니트", 2);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().page()).isEqualTo(2);
        verify(patternService).searchPatterns(user, "니트", 2);
    }

    @Test
    @DisplayName("대체 실 수정 API는 서비스 응답을 data에 담아 반환해야 한다")
    void updateAlternative_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        UpdateAlternativeYarnRequest request = new UpdateAlternativeYarnRequest("n", "./yarns/1.png", 100, 10000, "g", "s");
        when(patternService.updateAlternative(user, 10L, 1L, request))
                .thenReturn(new PatternAlternativesResponse.Item(1L, "n", "./yarns/1.png", 10000, 100, "g", "s", "admin"));

        ResponseEntity<ApiResponse<PatternAlternativesResponse.Item>> response =
                patternController.updateAlternative(user, 10L, 1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().altId()).isEqualTo(1L);
        verify(patternService).updateAlternative(user, 10L, 1L, request);
    }

    @Test
    @DisplayName("대체 실 삭제 API는 userId와 altId를 data에 담아 반환해야 한다")
    void deleteAlternative_ReturnsUserIdAndAltId() {
        User user = UserFixture.createUserWithId(1L);

        ResponseEntity<ApiResponse<PatternAlternativeDeleteResponse>> response = patternController.deleteAlternative(user, 10L, 1L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().userId()).isEqualTo(1L);
        assertThat(response.getBody().data().altId()).isEqualTo(1L);
        verify(patternService).deleteAlternative(user, 10L, 1L);
    }
}
