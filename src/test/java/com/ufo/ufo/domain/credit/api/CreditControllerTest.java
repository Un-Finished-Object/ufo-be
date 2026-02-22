package com.ufo.ufo.domain.credit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.dto.response.CreditRulesResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditTransactionsResponse;
import com.ufo.ufo.domain.credit.dto.response.CreditWalletResponse;
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
@DisplayName("크레딧 컨트롤러 테스트")
class CreditControllerTest {

    @Mock
    private CreditService creditService;

    @InjectMocks
    private CreditController creditController;

    @Test
    @DisplayName("내 크레딧 잔액 조회는 서비스 응답을 data에 담아 반환해야 한다")
    void getWallet_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(creditService.getWallet(user)).thenReturn(new CreditWalletResponse(12));

        ResponseEntity<ApiResponse<CreditWalletResponse>> response = creditController.getWallet(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().balance()).isEqualTo(12);
        assertThat(response.getBody().error()).isNull();
        verify(creditService).getWallet(user);
    }

    @Test
    @DisplayName("크레딧 변동 내역 조회는 서비스 응답을 data에 담아 반환해야 한다")
    void getTransactions_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(creditService.getTransactions(user, "SPEND", "CHATROOM_ENTRY", 2))
                .thenReturn(new CreditTransactionsResponse(List.of(), 2));

        ResponseEntity<ApiResponse<CreditTransactionsResponse>> response =
                creditController.getTransactions(user, "SPEND", "CHATROOM_ENTRY", 2);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().items()).isEmpty();
        assertThat(response.getBody().data().page()).isEqualTo(2);
        assertThat(response.getBody().error()).isNull();
        verify(creditService).getTransactions(user, "SPEND", "CHATROOM_ENTRY", 2);
    }

    @Test
    @DisplayName("크레딧 정책 조회는 서비스 응답을 data에 담아 반환해야 한다")
    void getRules_ReturnsServiceResponse() {
        when(creditService.getRules()).thenReturn(new CreditRulesResponse(20, List.of(), List.of()));

        ResponseEntity<ApiResponse<CreditRulesResponse>> response = creditController.getRules();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().dailyMaxEarnCredits()).isEqualTo(20);
        assertThat(response.getBody().error()).isNull();
        verify(creditService).getRules();
    }
}
