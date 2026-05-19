package com.ufo.ufo.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public String[] allowedOriginArray() {
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .toArray(String[]::new);
    }
}
