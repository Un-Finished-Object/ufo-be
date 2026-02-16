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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("Custom OidcUserService 테스트")
class CustomOidcUserServiceTest {

    @Mock
    private OAuthUserUpsertService oauthUserUpsertService;

    @Mock
    private OidcUserRequest userRequest;

    @Mock
    private ClientRegistration clientRegistration;

    @Test
    @DisplayName("Google OIDC 사용자 정보는 CustomOAuth2User로 변환되어야 한다")
    void loadUser_WithGoogle_MapsToCustomOAuth2User() {
        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);
        Map<String, Object> attributes = Map.of(
                "sub", "google-id",
                "email", "oidc@example.com",
                "name", "oidc-user",
                "picture", "https://example.com/oidc.png"
        );
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "google-id"));
        OidcUserInfo userInfo = new OidcUserInfo(attributes);

        when(oidcUser.getAttributes()).thenReturn(attributes);
        when(oidcUser.getIdToken()).thenReturn(idToken);
        when(oidcUser.getUserInfo()).thenReturn(userInfo);

        TestableCustomOidcUserService service = new TestableCustomOidcUserService(oauthUserUpsertService, oidcUser);
        User savedUser = UserFixture.createUser("oidc@example.com", Role.ROLE_USER);

        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getRegistrationId()).thenReturn("google");
        when(oauthUserUpsertService.saveOrUpdate(any(OAuth2Response.class))).thenReturn(savedUser);

        OidcUser result = service.loadUser(userRequest);

        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        CustomOAuth2User custom = (CustomOAuth2User) result;
        assertThat(custom.getEmail()).isEqualTo("oidc@example.com");
        assertThat(custom.getRoleKey()).isEqualTo("ROLE_USER");
        assertThat(custom.getIdToken()).isEqualTo(idToken);
        assertThat(custom.getUserInfo()).isEqualTo(userInfo);

        ArgumentCaptor<OAuth2Response> captor = ArgumentCaptor.forClass(OAuth2Response.class);
        org.mockito.Mockito.verify(oauthUserUpsertService).saveOrUpdate(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("oidc@example.com");
    }

    @Test
    @DisplayName("지원하지 않는 OIDC provider면 예외가 발생해야 한다")
    void loadUser_WithUnsupportedProvider_ThrowsException() {
        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);

        TestableCustomOidcUserService service = new TestableCustomOidcUserService(oauthUserUpsertService, oidcUser);
        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);
        when(clientRegistration.getRegistrationId()).thenReturn("unknown-provider");

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(UnsupportedProviderException.class);
    }

    private static class TestableCustomOidcUserService extends CustomOidcUserService {
        private final OidcUser oidcUser;

        private TestableCustomOidcUserService(OAuthUserUpsertService oauthUserUpsertService, OidcUser oidcUser) {
            super(oauthUserUpsertService);
            this.oidcUser = oidcUser;
        }

        @Override
        protected OidcUser fetchOidcUser(OidcUserRequest userRequest) {
            return oidcUser;
        }
    }
}
