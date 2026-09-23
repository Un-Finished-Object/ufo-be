package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.exception.AlternativeYarnNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternAlternativePermissionDeniedException;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternAlternativeYarnFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.YarnFixture;
import java.time.LocalDateTime;
import java.util.Optional;
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
    @DisplayName("대체 실 등록은 실과 도안 대체 실을 저장한다")
    void createAlternative_ValidRequest_SavesYarnAndAlternative() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));
        when(yarnRepository.save(any(Yarn.class))).thenAnswer(invocation -> {
            Yarn yarn = invocation.getArgument(0);
            YarnFixture.setId(yarn, 20L);
            return yarn;
        });
        when(patternAlternativeYarnRepository.save(any(PatternAlternativeYarn.class))).thenAnswer(invocation -> {
            PatternAlternativeYarn alternative = invocation.getArgument(0);
            PatternAlternativeYarnFixture.setId(alternative, 30L);
            return alternative;
        });

        var response = patternAlternativeService.createAlternative(user, 10L, createRequest());

        assertThat(response.altId()).isEqualTo(30L);
        assertThat(response.yarnId()).isEqualTo(20L);
        assertThat(response.yarnName()).isEqualTo("new yarn");
        assertThat(response.cost()).isEqualTo(10000);
        assertThat(response.username()).isEqualTo(user.getNickname());
        verify(yarnRepository).save(any(Yarn.class));
        verify(patternAlternativeYarnRepository).save(any(PatternAlternativeYarn.class));
    }

    @Test
    @DisplayName("게스트는 대체 실을 등록할 수 없다")
    void createAlternative_Guest_ThrowsForbidden() {
        User guest = guest();

        assertThatThrownBy(() -> patternAlternativeService.createAlternative(guest, 10L, createRequest()))
                .isInstanceOf(PatternAlternativePermissionDeniedException.class);
        verifyNoInteractions(patternRepository, patternAlternativeYarnRepository, yarnRepository);
    }

    @Test
    @DisplayName("삭제된 도안에는 대체 실을 등록할 수 없다")
    void createAlternative_DeletedPattern_ThrowsNotFound() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(pattern, LocalDateTime.now());
        when(patternRepository.findById(10L)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> patternAlternativeService.createAlternative(user, 10L, createRequest()))
                .isInstanceOf(PatternNotFoundException.class);
        verifyNoInteractions(yarnRepository, patternAlternativeYarnRepository);
    }

    @Test
    @DisplayName("작성자는 대체 실의 실 정보를 수정할 수 있다")
    void updateAlternative_Owner_UpdatesYarn() {
        User owner = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = existingAlternative(owner);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(alternative.getPattern()));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L))
                .thenReturn(Optional.of(alternative));

        var response = patternAlternativeService.updateAlternative(owner, 10L, 30L, updateRequest());

        assertThat(response.altId()).isEqualTo(30L);
        assertThat(response.yarnName()).isEqualTo("updated yarn");
        assertThat(response.cost()).isEqualTo(20000);
        assertThat(alternative.getYarn().getName()).isEqualTo("updated yarn");
    }

    @Test
    @DisplayName("다른 사용자는 대체 실을 수정할 수 없다")
    void updateAlternative_NotOwner_ThrowsForbidden() {
        User owner = UserFixture.createUserWithId(2L);
        User requester = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = existingAlternative(owner);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(alternative.getPattern()));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L))
                .thenReturn(Optional.of(alternative));

        assertThatThrownBy(() -> patternAlternativeService.updateAlternative(requester, 10L, 30L, updateRequest()))
                .isInstanceOf(PatternAlternativePermissionDeniedException.class);
        assertThat(alternative.getYarn().getName()).isEqualTo("old");
    }

    @Test
    @DisplayName("없는 대체 실 수정은 실패한다")
    void updateAlternative_NotFound_ThrowsException() {
        User user = UserFixture.createUserWithId(1L);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(PatternFixture.createPatternWithId(10L)));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patternAlternativeService.updateAlternative(user, 10L, 99L, updateRequest()))
                .isInstanceOf(AlternativeYarnNotFoundException.class);
    }

    @Test
    @DisplayName("작성자는 대체 실을 삭제할 수 있다")
    void deleteAlternative_Owner_DeletesAlternative() {
        User owner = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = existingAlternative(owner);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(alternative.getPattern()));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L))
                .thenReturn(Optional.of(alternative));

        patternAlternativeService.deleteAlternative(owner, 10L, 30L);

        verify(patternAlternativeYarnRepository).deleteById(30L);
    }

    @Test
    @DisplayName("다른 사용자는 대체 실을 삭제할 수 없다")
    void deleteAlternative_NotOwner_ThrowsForbidden() {
        User owner = UserFixture.createUserWithId(2L);
        User requester = UserFixture.createUserWithId(1L);
        PatternAlternativeYarn alternative = existingAlternative(owner);
        when(patternRepository.findById(10L)).thenReturn(Optional.of(alternative.getPattern()));
        when(patternAlternativeYarnRepository.findByIdAndPattern_Id(30L, 10L))
                .thenReturn(Optional.of(alternative));

        assertThatThrownBy(() -> patternAlternativeService.deleteAlternative(requester, 10L, 30L))
                .isInstanceOf(PatternAlternativePermissionDeniedException.class);
        verify(patternAlternativeYarnRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("게스트는 대체 실을 삭제할 수 없다")
    void deleteAlternative_Guest_ThrowsForbidden() {
        assertThatThrownBy(() -> patternAlternativeService.deleteAlternative(guest(), 10L, 30L))
                .isInstanceOf(PatternAlternativePermissionDeniedException.class);
        verifyNoInteractions(patternRepository, patternAlternativeYarnRepository);
    }

    private User guest() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 1L);
        return guest;
    }

    private PatternAlternativeYarn existingAlternative(User owner) {
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        Yarn yarn = YarnFixture.createYarnWithId(20L);
        return PatternAlternativeYarnFixture.createWithId(30L, pattern, owner, yarn);
    }

    private CreateAlternativeRequest createRequest() {
        return new CreateAlternativeRequest("new yarn", 100, 10000, "wool", "nylon", "store", "2", 180);
    }

    private UpdateAlternativeYarnRequest updateRequest() {
        return new UpdateAlternativeYarnRequest("updated yarn", 120, 20000, "wool", "nylon", "store", "1", 140);
    }
}
