package com.ufo.ufo.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Provider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth 사용자 업서트 서비스 테스트")
class OAuthUserUpsertServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OAuthUserUpsertService oauthUserUpsertService;

    @Test
    @DisplayName("존재하지 않는 사용자면 신규 사용자로 생성 저장해야 한다")
    void saveOrUpdate_WhenUserNotExists_CreatesUser() {
        OAuth2Response response = oauthResponse(
                "new@example.com", "new-user", "https://example.com/new.png", Provider.GOOGLE
        );
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = oauthUserUpsertService.saveOrUpdate(response);

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getNickname()).isEqualTo("new-user");
        assertThat(saved.getProfileImage()).isEqualTo("https://example.com/new.png");
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_GUEST);
        assertThat(saved.getProvider()).isEqualTo(Provider.GOOGLE);
    }

    @Test
    @DisplayName("이미 존재하는 사용자면 기존 닉네임과 프로필 이미지를 유지한 채 저장해야 한다")
    void saveOrUpdate_WhenUserExists_PreservesProfile() {
        User existing = UserFixture.createUser("exists@example.com", Role.ROLE_USER);
        OAuth2Response response = oauthResponse(
                "exists@example.com", "updated-name", "https://example.com/updated.png", Provider.GOOGLE
        );
        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = oauthUserUpsertService.saveOrUpdate(response);

        assertThat(saved.getNickname()).isEqualTo("tester");
        assertThat(saved.getProfileImage()).isEqualTo("https://example.com/profile.png");
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getProvider()).isEqualTo(Provider.GOOGLE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    private OAuth2Response oauthResponse(String email, String name, String profileImage, Provider provider) {
        return new OAuth2Response() {
            @Override
            public Provider getProvider() {
                return provider;
            }

            @Override
            public String getProviderId() {
                return "provider-id";
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getProfileImage() {
                return profileImage;
            }
        };
    }
}
