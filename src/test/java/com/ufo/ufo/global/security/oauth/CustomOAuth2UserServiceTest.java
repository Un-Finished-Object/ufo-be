package com.ufo.ufo.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UnsupportedProviderException;
import com.ufo.ufo.global.security.dto.OAuth2Response;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
@DisplayName("Custom OAuth2UserService 테스트")
class CustomOAuth2UserServiceTest {

    @Mock
    private OAuthUserUpsertService oauthUserUpsertService;

    @Mock
    private OAuth2UserRequest userRequest;

    @Mock
    private ClientRegistration clientRegistration;

    @Test
    @DisplayName("Google OAuth2 사용자 정보는 CustomOAuth2User로 변환되어야 한다")
    void loadUser_WithGoogle_MapsToCustomOAuth2User() {
        OAuth2User oauth2User = org.mockito.Mockito.mock(OAuth2User.class);
        Map<String, Object> attributes = Map.of(
                "sub", "google-id",
                "email", "google@example.com",
                "name", "google-user",
                "picture", "https://example.com/google.png"
        );
        when(oauth2User.getAttributes()).thenReturn(attributes);

        TestableCustomOAuth2UserService service = new TestableCustomOAuth2UserService(oauthUserUpsertService, oauth2User);
        User savedUser = UserFixture.createUser("google@example.com", Role.ROLE_USER);

        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getRegistrationId()).thenReturn("google");
        when(oauthUserUpsertService.saveOrUpdate(any(OAuth2Response.class))).thenReturn(savedUser);

        OAuth2User result = service.loadUser(userRequest);

        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        CustomOAuth2User custom = (CustomOAuth2User) result;
        assertThat(custom.getEmail()).isEqualTo("google@example.com");
        assertThat(custom.getRoleKey()).isEqualTo("ROLE_USER");

        ArgumentCaptor<OAuth2Response> captor = ArgumentCaptor.forClass(OAuth2Response.class);
        org.mockito.Mockito.verify(oauthUserUpsertService).saveOrUpdate(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("google@example.com");
    }

    @Test
    @DisplayName("지원하지 않는 provider면 예외가 발생해야 한다")
    void loadUser_WithUnsupportedProvider_ThrowsException() {
        OAuth2User oauth2User = org.mockito.Mockito.mock(OAuth2User.class);
        TestableCustomOAuth2UserService service = new TestableCustomOAuth2UserService(oauthUserUpsertService, oauth2User);

        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getRegistrationId()).thenReturn("unknown-provider");

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(UnsupportedProviderException.class);
    }

    private static class TestableCustomOAuth2UserService extends CustomOAuth2UserService {
        private final OAuth2User oauth2User;

        private TestableCustomOAuth2UserService(OAuthUserUpsertService oauthUserUpsertService, OAuth2User oauth2User) {
            super(oauthUserUpsertService);
            this.oauth2User = oauth2User;
        }

        @Override
        protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
            return oauth2User;
        }
    }
}
