package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.exception.PatternAlternativePermissionDeniedException;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 대체 실 서비스 테스트")
class PatternAlternativeServiceTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternAlternativeYarnRepository patternAlternativeYarnRepository;

    @Mock
    private YarnRepository yarnRepository;

    @InjectMocks
    private PatternAlternativeService patternAlternativeService;

    @Test
    @DisplayName("게스트 사용자는 대체 실을 등록할 수 없다")
    void createAlternative_Guest_ThrowsForbidden() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 1L);
        CreateAlternativeRequest request = new CreateAlternativeRequest(
                "name", 100, 10000, "main", "sub", "store", "2", 180
        );

        assertThatThrownBy(() -> patternAlternativeService.createAlternative(guest, 10L, request))
                .isInstanceOf(PatternAlternativePermissionDeniedException.class);
        verifyNoInteractions(patternRepository, patternAlternativeYarnRepository, yarnRepository);
    }
}
