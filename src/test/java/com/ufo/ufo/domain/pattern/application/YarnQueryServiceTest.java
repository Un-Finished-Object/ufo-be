package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.response.YarnResponse;
import com.ufo.ufo.domain.pattern.exception.YarnNotFoundException;
import com.ufo.ufo.support.fixture.YarnFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("실 조회 서비스 테스트")
class YarnQueryServiceTest {

    @Mock
    private YarnRepository yarnRepository;

    @InjectMocks
    private YarnQueryService yarnQueryService;

    @Test
    @DisplayName("실 상세 조회는 응답 DTO로 변환해서 반환해야 한다")
    void getYarnDetail_ReturnsYarnResponse() {
        Yarn yarn = YarnFixture.createYarnWithId(10L);
        YarnFixture.setPly(yarn, 4);
        when(yarnRepository.findByYarnIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(yarn));

        YarnResponse response = yarnQueryService.getYarnDetail(10L);

        assertThat(response.yarnId()).isEqualTo(10L);
        assertThat(response.yarnName()).isEqualTo("old");
        assertThat(response.ply()).isEqualTo(4);
        assertThat(response.weight()).isEqualTo(100);
        assertThat(response.cost()).isEqualTo(1000);
        assertThat(response.component()).isEqualTo("wool 80%, nylon 20%");
        assertThat(response.store()).isEqualTo("oldV");
        assertThat(response.length()).isEqualTo(120);
    }

    @Test
    @DisplayName("존재하지 않는 실 조회는 예외가 발생해야 한다")
    void getYarnDetail_WhenMissing_ThrowsNotFound() {
        when(yarnRepository.findByYarnIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> yarnQueryService.getYarnDetail(99L))
                .isInstanceOf(YarnNotFoundException.class);
    }
}
