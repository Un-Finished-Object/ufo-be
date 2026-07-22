package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternOriginalYarnRepository;
import com.ufo.ufo.domain.pattern.dao.YarnAlternativeRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import com.ufo.ufo.domain.pattern.dto.response.YarnAlternativesResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnAlternativeFixture;
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
    private YarnAlternativeRepository yarnAlternativeRepository;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private AlternativeYarnQueryService alternativeYarnQueryService;

    @Test
    @DisplayName("각 원작 실을 기준으로 서로 다른 대체 실 목록을 반환해야 한다")
    void getAlternatives_ReturnsAlternativesForEachOriginalYarn() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        Yarn mainYarn = YarnFixture.createYarnWithId(11L);
        Yarn secondYarn = YarnFixture.createYarnWithId(12L);
        PatternOriginalYarn originalYarnSet = PatternFixture.setOriginalYarn(
                pattern,
                mainYarn,
                secondYarn,
                null
        );
        PatternFixture.setOriginalYarnId(originalYarnSet, 20L);
        YarnAlternative mainAlternative = YarnAlternativeFixture.createWithId(
                30L,
                mainYarn,
                YarnFixture.createYarnWithId(13L)
        );
        YarnAlternative secondAlternative = YarnAlternativeFixture.createWithId(
                31L,
                secondYarn,
                YarnFixture.createYarnWithId(14L)
        );

        when(patternOriginalYarnRepository.findActiveSetById(20L)).thenReturn(Optional.of(originalYarnSet));
        when(creditService.isUnlocked(1L, 10L, UnlockType.YARN_INFO)).thenReturn(true);
        when(yarnAlternativeRepository.findAllByOriginalYarnId(11L)).thenReturn(List.of(mainAlternative));
        when(yarnAlternativeRepository.findAllByOriginalYarnId(12L)).thenReturn(List.of(secondAlternative));

        YarnAlternativesResponse response = alternativeYarnQueryService.getAlternatives(user, 20L);

        assertThat(response.firstYarn()).extracting(YarnAlternativesResponse.Item::altId).containsExactly(30L);
        assertThat(response.secondYarn()).extracting(YarnAlternativesResponse.Item::altId).containsExactly(31L);
        assertThat(response.subYarn()).isEmpty();
        verify(yarnAlternativeRepository).findAllByOriginalYarnId(11L);
        verify(yarnAlternativeRepository).findAllByOriginalYarnId(12L);
        verify(yarnAlternativeRepository, never()).findAllByOriginalYarnId(null);
    }
}
