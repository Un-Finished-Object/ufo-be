package com.ufo.ufo.global.security.config;

import com.ufo.ufo.global.security.filter.JwtAuthenticationFilter;
import com.ufo.ufo.global.security.handler.OAuth2SuccessHandler;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.ufo.ufo.global.security.oauth.CustomOidcUserService;
import com.ufo.ufo.global.security.oauth.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/auth/login/**", "/v1/auth/oauth/**", "/v1/auth/token/refresh").permitAll()
                        .requestMatchers("/login/**", "/oauth2/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/patterns", "/v1/patterns/search", "/v1/patterns/recommend").permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/v1/patterns/\\d+$", HttpMethod.GET.name())).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/v1/yarns/\\d+$", HttpMethod.GET.name())).permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
                        .redirectionEndpoint(redirection ->
                                redirection.baseUri("/v1/auth/oauth/*/callback"))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler(oAuth2SuccessHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
