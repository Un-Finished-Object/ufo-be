package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternOriginalYarnRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.dto.response.YarnAlternativesResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternAlternativeYarnFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("대체 실 조회 서비스 테스트")
class AlternativeYarnQueryServiceTest {

    @Mock
    private PatternOriginalYarnRepository patternOriginalYarnRepository;

    @Mock
    private PatternAlternativeYarnRepository patternAlternativeYarnRepository;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private AlternativeYarnQueryService alternativeYarnQueryService;

    @Test
    @DisplayName("도안 대체 실 테이블 데이터를 원작 실 슬롯에 반환해야 한다")
    void getAlternatives_ReturnsStoredPatternAlternatives() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternOriginalYarn originalYarnSet = PatternFixture.setOriginalYarn(
                pattern,
                YarnFixture.createYarnWithId(11L),
                YarnFixture.createYarnWithId(12L),
                null
        );
        PatternFixture.setOriginalYarnId(originalYarnSet, 20L);
        PatternAlternativeYarn alternative = PatternAlternativeYarnFixture.createWithId(
                30L,
                pattern,
                user,
                YarnFixture.createYarnWithId(13L)
        );

        when(patternOriginalYarnRepository.findActiveSetById(20L)).thenReturn(Optional.of(originalYarnSet));
        when(creditService.isUnlocked(1L, 10L, UnlockType.YARN_INFO)).thenReturn(true);
        when(patternAlternativeYarnRepository.findActiveByPatternId(10L)).thenReturn(List.of(alternative));

        YarnAlternativesResponse response = alternativeYarnQueryService.getAlternatives(user, 20L);

        assertThat(response.firstYarn()).extracting(YarnAlternativesResponse.Item::altId).containsExactly(30L);
        assertThat(response.secondYarn()).extracting(YarnAlternativesResponse.Item::altId).containsExactly(30L);
        assertThat(response.subYarn()).isEmpty();
        verify(patternAlternativeYarnRepository).findActiveByPatternId(10L);
    }
}
