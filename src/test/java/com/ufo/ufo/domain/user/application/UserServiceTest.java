package com.ufo.ufo.domain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.request.UpdateMyInfoRequest;
import com.ufo.ufo.domain.user.dto.response.UpdateMyInfoResponse;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("User 서비스 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("사용자 이메일이 존재하면 사용자 응답 정보를 반환해야 한다")
    void getUserInfo_WhenUserExists_ReturnsUserResponse() {
        String email = "test@example.com";
        User user = UserFixture.createUser(email, Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UserFixture.setCreatedAt(user, LocalDate.now().minusDays(5).atStartOfDay());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserInfo(email);

        assertThat(response.email()).isEqualTo(email);
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.profileImage()).isEqualTo("https://example.com/profile.png");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.joinDate()).isEqualTo(5);
    }

    @Test
    @DisplayName("사용자 이메일이 존재하지 않으면 UserNotFoundException이 발생해야 한다")
    void getUserInfo_WhenUserMissing_ThrowsUserNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserInfo("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("내 정보 수정 시 사용자 정보를 갱신하고 수정 결과를 반환해야 한다")
    void updateMyInfo_WhenUserExists_UpdatesUserAndReturnsResponse() {
        User user = UserFixture.createUser("test@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest("updatedName", "https://example.com/updated.png");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UpdateMyInfoResponse response = userService.updateMyInfo(user, request);

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.nickname()).isEqualTo("updatedName");
        assertThat(response.profileImage()).isEqualTo("https://example.com/updated.png");
        assertThat(user.getNickname()).isEqualTo("updatedName");
        assertThat(user.getProfileImage()).isEqualTo("https://example.com/updated.png");
    }

    @Test
    @DisplayName("내 정보 수정 시 사용자가 존재하지 않으면 UserNotFoundException이 발생해야 한다")
    void updateMyInfo_WhenUserMissing_ThrowsUserNotFoundException() {
        User user = UserFixture.createUser("test@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest("updatedName", "https://example.com/updated.png");
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMyInfo(user, request))
                .isInstanceOf(UserNotFoundException.class);
    }
}
