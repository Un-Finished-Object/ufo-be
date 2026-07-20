package com.ufo.ufo.domain.pattern.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.application.PatternService;
import com.ufo.ufo.domain.pattern.application.PatternPurchaseService;
import com.ufo.ufo.domain.pattern.dto.request.AlternativeGaugeRequest;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeDeleteResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativesResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternMyResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternStatsResponse;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.PatternScrapResponse;
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

    @Mock
    private PatternPurchaseService patternPurchaseService;

    @Mock
    private ScrapService scrapService;

    @InjectMocks
    private PatternController patternController;

    @Test
    @DisplayName("도안 목록 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getPatterns_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternService.getPatterns(user, "apparel", "sweater", "news", 1))
                .thenReturn(new PatternListResponse(List.of(), 1, 0));

        ResponseEntity<ApiResponse<PatternListResponse>> response =
                patternController.getPatterns(user, "apparel", "sweater", "news", 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().page()).isEqualTo(1);
        verify(patternService).getPatterns(user, "apparel", "sweater", "news", 1);
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
        CreateAlternativeRequest request = createAlternativeRequest();
        when(patternService.createAlternative(user, 10L, request))
                .thenReturn(sampleAlternativeItem());

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
                .thenReturn(new PatternListResponse(List.of(), 2, 0));

        ResponseEntity<ApiResponse<PatternListResponse>> response = patternController.searchPatterns(user, "니트", 2);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().page()).isEqualTo(2);
        verify(patternService).searchPatterns(user, "니트", 2);
    }

    @Test
    @DisplayName("대체 실 수정 API는 서비스 응답을 data에 담아 반환해야 한다")
    void updateAlternative_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        UpdateAlternativeYarnRequest request = updateAlternativeRequest();
        when(patternService.updateAlternative(user, 10L, 1L, request))
                .thenReturn(sampleAlternativeItem());

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

    @Test
    @DisplayName("구매 API는 서비스 응답을 data에 담아 반환해야 한다")
    void purchase_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        PatternPurchaseRequest request = new PatternPurchaseRequest("chat");
        when(patternPurchaseService.purchase(user, 10L, request))
                .thenReturn(new PatternPurchaseResponse(1L, "chat"));

        ResponseEntity<ApiResponse<PatternPurchaseResponse>> response = patternController.purchase(user, 10L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().userId()).isEqualTo(1L);
        assertThat(response.getBody().data().type()).isEqualTo("chat");
        verify(patternPurchaseService).purchase(user, 10L, request);
    }

    @Test
    @DisplayName("구매 여부 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getPurchaseStatus_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(patternPurchaseService.getStatus(user, 10L))
                .thenReturn(PatternPurchaseStatusResponse.from(1L, true, false, 20L));

        ResponseEntity<ApiResponse<PatternPurchaseStatusResponse>> response = patternController.getPurchaseStatus(user, 10L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().userId()).isEqualTo(1L);
        assertThat(response.getBody().data().chat()).isTrue();
        assertThat(response.getBody().data().alternative()).isFalse();
        assertThat(response.getBody().data().chatRoomId()).isEqualTo(20L);
        verify(patternPurchaseService).getStatus(user, 10L);
    }

    @Test
    @DisplayName("도안 찜 추가 API는 서비스 응답을 data에 담아 반환해야 한다")
    void addPatternScrap_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(scrapService.addPatternScrap(user, 10L))
                .thenReturn(PatternScrapResponse.from(true, 34));

        ResponseEntity<ApiResponse<PatternScrapResponse>> response = patternController.addPatternScrap(user, 10L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().scrapped()).isTrue();
        assertThat(response.getBody().data().scrapCount()).isEqualTo(34);
        verify(scrapService).addPatternScrap(user, 10L);
    }

    @Test
    @DisplayName("도안 찜 취소 API는 서비스 응답을 data에 담아 반환해야 한다")
    void removePatternScrap_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(scrapService.removePatternScrap(user, 10L))
                .thenReturn(PatternScrapResponse.from(false, 33));

        ResponseEntity<ApiResponse<PatternScrapResponse>> response = patternController.removePatternScrap(user, 10L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().scrapped()).isFalse();
        assertThat(response.getBody().data().scrapCount()).isEqualTo(33);
        verify(scrapService).removePatternScrap(user, 10L);
    }

    private CreateAlternativeRequest createAlternativeRequest() {
        return new CreateAlternativeRequest(
                "n",
                100,
                10000,
                "알파카",
                "알파카 90%, 나일론 10%",
                "s",
                "2",
                180,
                List.of(new AlternativeGaugeRequest("5.5", 17, 24))
        );
    }

    private UpdateAlternativeYarnRequest updateAlternativeRequest() {
        return new UpdateAlternativeYarnRequest(
                "n",
                100,
                10000,
                "알파카",
                "알파카 90%, 나일론 10%",
                "s",
                "2",
                180,
                List.of(new AlternativeGaugeRequest("5.5", 17, 24))
        );
    }

    private PatternAlternativesResponse.Item sampleAlternativeItem() {
        return new PatternAlternativesResponse.Item(
                1L,
                2L,
                "n",
                100,
                10000,
                "알파카",
                "알파카 90%, 나일론 10%",
                "s",
                "2",
                180,
                List.of(new PatternAlternativesResponse.Item.Gauge("5.5", 17, 24)),
                "admin"
        );
    }
}
