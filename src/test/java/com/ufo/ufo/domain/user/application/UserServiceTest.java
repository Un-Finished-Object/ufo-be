package com.ufo.ufo.domain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.application.ImageService;
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

    @Mock
    private ImageService imageService;

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
        assertThat(response.userName()).isEqualTo("updatedName");
        assertThat(response.profileImage()).isEqualTo("https://example.com/updated.png");
        assertThat(user.getNickname()).isEqualTo("updatedName");
        assertThat(user.getProfileImage()).isEqualTo("https://example.com/updated.png");
    }

    @Test
    @DisplayName("내 정보 수정 시 profileImage가 없으면 기존 이미지를 유지해야 한다")
    void updateMyInfo_WhenProfileImageMissing_KeepsExistingProfileImage() {
        User user = UserFixture.createUser("test@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest("updatedName", null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UpdateMyInfoResponse response = userService.updateMyInfo(user, request);

        assertThat(response.userName()).isEqualTo("updatedName");
        assertThat(response.profileImage()).isEqualTo("https://example.com/profile.png");
        assertThat(user.getNickname()).isEqualTo("updatedName");
        assertThat(user.getProfileImage()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    @DisplayName("내 정보 수정 시 profileImage가 있으면 소유권 검증 후 저장해야 한다")
    void updateMyInfo_WhenProfileImageProvided_ValidatesOwnershipBeforeSaving() {
        User user = UserFixture.createUser("test@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest("updatedName", "https://cdn.ufo.com/profiles/10/image");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UpdateMyInfoResponse response = userService.updateMyInfo(user, request);

        assertThat(response.userName()).isEqualTo("updatedName");
        assertThat(response.profileImage()).isEqualTo("https://cdn.ufo.com/profiles/10/image");
        assertThat(user.getProfileImage()).isEqualTo("https://cdn.ufo.com/profiles/10/image");
        verify(imageService).validateProfileImage(user, "https://cdn.ufo.com/profiles/10/image");
    }

    @Test
    @DisplayName("내 정보 수정 시 userName이 없으면 기존 닉네임을 유지해야 한다")
    void updateMyInfo_WhenUserNameMissing_KeepsExistingUserName() {
        User user = UserFixture.createUser("test@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest(null, "https://example.com/updated.png");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UpdateMyInfoResponse response = userService.updateMyInfo(user, request);

        assertThat(response.userName()).isEqualTo("tester");
        assertThat(response.profileImage()).isEqualTo("https://example.com/updated.png");
        assertThat(user.getNickname()).isEqualTo("tester");
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
