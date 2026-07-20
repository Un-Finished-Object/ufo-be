package com.ufo.ufo.domain.image.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        int maxFileCount,
        long maxBytes,
        List<String> allowedContentTypes,
        String cdnBaseUrl,
        String defaultProfileImageKey,
        S3 s3
) {
    public record S3(
            String bucket,
            String region,
            long urlExpirationMinutes,
            String publicBaseUrl
    ) {
    }
}
