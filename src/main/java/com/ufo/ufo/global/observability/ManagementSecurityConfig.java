package com.ufo.ufo.global.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ManagementSecurityConfig {

    private final Environment environment;

    @Bean
    @Order(0)
    SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to("health")).access((authentication, context) -> {
                            int managementPort = environment.getProperty("local.management.port", Integer.class, -1);
                            int applicationPort = environment.getProperty("local.server.port", Integer.class, -1);
                            return new AuthorizationDecision(managementPort != applicationPort
                                    && context.getRequest().getLocalPort() == managementPort);
                        })
                        .anyRequest().denyAll())
                .build();
    }
}
