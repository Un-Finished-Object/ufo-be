package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufo.ufo.domain.image.config.ImageProperties;
import com.ufo.ufo.global.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3 POST 정책 서명 테스트")
class S3PostPolicySignerTest {

    private static final ImageProperties IMAGE_PROPERTIES = new ImageProperties(
            5, 10_485_760L, List.of("image/jpeg"), "https://cdn.example.com", "defaults/profile.png",
            new ImageProperties.S3("bucket", "ap-northeast-2", 5L, "https://s3.example.com")
    );
    private static final String ISSUED_TAGGING_XML = "<Tagging><TagSet><Tag>"
            + "<Key>ufo-upload-status</Key><Value>issued</Value>"
            + "</Tag></TagSet></Tagging>";

    @Mock
    private AwsCredentialsProvider credentialsProvider;

    @Test
    @DisplayName("Presigned POST 발급 시 파일 크기 제한을 정책에 포함하고 자격 증명은 한 번만 조회해야 한다")
    void signsUploadPolicyWithFileLimitUsingOneCredentialsLookup() {
        var form = createForm(AwsBasicCredentials.create("access-key", "secret-key"));

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

    @Test
    @DisplayName("Presigned POST 폼과 정책에는 동일한 XML 태그가 포함되어야 한다")
    void includesIssuedXmlTagInFormAndPolicy() throws Exception {
        var form = createForm(AwsBasicCredentials.create("access-key", "secret-key"));

        assertThat(form.uploadFields()).containsEntry("tagging", ISSUED_TAGGING_XML)
                .doesNotContainKeys("x-amz-tagging", "x-amz-security-token");
        var policy = new ObjectMapper().readTree(Base64.getDecoder().decode(form.uploadFields().get("policy")));
        assertThat(policy.path("conditions").findValuesAsText("tagging")).containsExactly(ISSUED_TAGGING_XML);
        assertThat(policy.path("conditions").findValues("x-amz-tagging")).isEmpty();
        assertThat(policy.path("conditions").findValues("x-amz-security-token")).isEmpty();
    }

    @Test
    @DisplayName("임시 자격 증명으로 발급한 폼과 정책에는 XML 태그와 세션 토큰이 포함되어야 한다")
    void preservesSessionTokenWithXmlTagging() throws Exception {
        var form = createForm(
                AwsSessionCredentials.create("session-access-key", "session-secret-key", "session-token")
        );

        assertThat(form.uploadFields()).containsEntry("tagging", ISSUED_TAGGING_XML)
                .containsEntry("x-amz-security-token", "session-token")
                .containsEntry("x-amz-credential", "session-access-key/20260923/ap-northeast-2/s3/aws4_request")
                .doesNotContainKey("x-amz-tagging");
        var policy = new ObjectMapper().readTree(Base64.getDecoder().decode(form.uploadFields().get("policy")));
        assertThat(policy.path("conditions").findValuesAsText("tagging")).containsExactly(ISSUED_TAGGING_XML);
        assertThat(policy.path("conditions").findValuesAsText("x-amz-security-token")).containsExactly("session-token");
        assertThat(policy.path("conditions").findValuesAsText("x-amz-credential"))
                .containsExactly("session-access-key/20260923/ap-northeast-2/s3/aws4_request");
        assertThat(policy.path("conditions").findValues("x-amz-tagging")).isEmpty();
        verify(credentialsProvider).resolveCredentials();
    }

    @Test
    @DisplayName("정책 JSON 생성에 실패하면 원인을 보존한 API 서버 오류를 반환한다")
    void reportsPolicySerializationFailure() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        var failure = new JsonProcessingException("serialization-secret") { };
        when(mapper.writeValueAsString(any())).thenThrow(failure);
        when(credentialsProvider.resolveCredentials()).thenReturn(AwsBasicCredentials.create("access", "secret"));
        var signer = new S3PostPolicySigner(credentialsProvider, IMAGE_PROPERTIES, mapper);

        assertThatThrownBy(() -> signer.createForm(
                "profiles/1/avatar.png", "image/jpeg", Instant.parse("2026-09-23T00:00:00Z"), Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getMessage()).doesNotContain("serialization-secret");
                    assertThat(exception.getCause()).isSameAs(failure);
                });
    }

    @Test
    @DisplayName("서명 알고리즘 초기화에 실패하면 원인을 보존한 API 서버 오류를 반환한다")
    void reportsSignatureFailure() {
        var failure = new NoSuchAlgorithmException("algorithm-secret");
        try (var mac = mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA256")).thenThrow(failure);

            assertThatThrownBy(() -> createForm(AwsBasicCredentials.create("access", "secret")))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                        assertThat(exception.getMessage()).doesNotContain("algorithm-secret");
                        assertThat(exception.getCause()).isSameAs(failure);
                    });
        }
    }

    private S3PostPolicySigner.PostForm createForm(AwsCredentials credentials) {
        when(credentialsProvider.resolveCredentials()).thenReturn(credentials);
        var signer = new S3PostPolicySigner(credentialsProvider, IMAGE_PROPERTIES, new ObjectMapper());
        return signer.createForm(
                "profiles/1/avatar.png", "image/jpeg", Instant.parse("2026-09-23T00:00:00Z"), Duration.ofMinutes(5)
        );
    }
}
