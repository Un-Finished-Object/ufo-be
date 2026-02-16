package com.ufo.ufo.global.security.oauth;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class CustomOAuth2User implements OAuth2User, OidcUser {

    private final String email;
    private final String roleKey;
    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public CustomOAuth2User(String email, String roleKey, Map<String, Object> attributes) {
        this(email, roleKey, attributes, null, null);
    }

    public CustomOAuth2User(String email, String roleKey, Map<String, Object> attributes,
                            OidcIdToken idToken, OidcUserInfo userInfo) {
        this.email = email;
        this.roleKey = roleKey;
        this.attributes = attributes;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(roleKey));
    }

    @Override
    public String getName() {
        return email;
    }

    @Override
    public Map<String, Object> getClaims() {
        if (idToken == null) {
            return attributes;
        }
        return idToken.getClaims();
    }
}
