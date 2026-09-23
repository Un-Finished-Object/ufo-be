package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufo.ufo.domain.image.config.ImageProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@ExtendWith(MockitoExtension.class)
class S3PostPolicySignerTest {

    @Mock
    private AwsCredentialsProvider credentialsProvider;

    @Test
    void signsUploadPolicyWithFileLimitUsingOneCredentialsLookup() {
        var properties = new ImageProperties(
                5, 10_485_760L, List.of("image/jpeg"), "https://cdn.example.com", "defaults/profile.png",
                new ImageProperties.S3("bucket", "ap-northeast-2", 5L, "https://s3.example.com")
        );
        when(credentialsProvider.resolveCredentials())
                .thenReturn(AwsBasicCredentials.create("access-key", "secret-key"));
        var signer = new S3PostPolicySigner(credentialsProvider, properties, new ObjectMapper());

        var form = signer.createForm(
                "profiles/1/avatar.png", "image/jpeg", Instant.parse("2026-09-23T00:00:00Z"), Duration.ofMinutes(5)
        );

        assertThat(form.endpointUrl()).isEqualTo("https://s3.example.com");
        assertThat(form.uploadFields()).containsEntry("key", "profiles/1/avatar.png")
                .containsEntry("Content-Type", "image/jpeg")
                .containsKey("x-amz-signature");
        String policy = new String(
                Base64.getDecoder().decode(form.uploadFields().get("policy")), StandardCharsets.UTF_8
        );
        assertThat(policy).contains("\"content-length-range\",1,10485760");
        verify(credentialsProvider).resolveCredentials();
    }
}
