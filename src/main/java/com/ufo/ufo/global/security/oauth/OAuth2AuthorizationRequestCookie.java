package com.ufo.ufo.global.security.oauth;

import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public record OAuth2AuthorizationRequestCookie(
        String authorizationUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        Map<String, Object> attributes,
        String authorizationRequestUri
) {

    public static OAuth2AuthorizationRequestCookie from(OAuth2AuthorizationRequest authorizationRequest) {
        return new OAuth2AuthorizationRequestCookie(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(),
                authorizationRequest.getScopes(),
                authorizationRequest.getState(),
                authorizationRequest.getAdditionalParameters(),
                authorizationRequest.getAttributes(),
                authorizationRequest.getAuthorizationRequestUri()
        );
    }

    public OAuth2AuthorizationRequest toAuthorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes)
                .state(state)
                .additionalParameters(additionalParameters)
                .attributes(attributes)
                .authorizationRequestUri(authorizationRequestUri)
                .build();
    }
}
