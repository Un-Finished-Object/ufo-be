package com.ufo.ufo.global.config;

import com.ufo.ufo.domain.image.config.ImageProperties;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(ImageProperties imageProperties) {
        return S3Presigner.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(imageProperties.s3().region()))
                .build();
    }
}
