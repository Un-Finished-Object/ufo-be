package com.ufo.ufo.global.config;

import com.ufo.ufo.domain.image.config.ImageProperties;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Presigner s3Presigner(ImageProperties imageProperties, AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(imageProperties.s3().region()))
                .build();
    }

    @Bean
    public S3Client s3Client(ImageProperties imageProperties, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(imageProperties.s3().region()))
                .build();
    }
}
