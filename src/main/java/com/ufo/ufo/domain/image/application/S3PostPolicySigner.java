package com.ufo.ufo.domain.image.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufo.ufo.domain.image.config.ImageProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

@Component
@RequiredArgsConstructor
public class S3PostPolicySigner {

    private static final DateTimeFormatter UTC_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter UTC_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final String UPLOAD_STATUS_TAG_KEY = "ufo-upload-status";
    private static final String UPLOAD_STATUS_ISSUED = "issued";
    private static final String S3_TAGGING_FIELD = "tagging";
    private static final String AWS_ALGORITHM = "AWS4-HMAC-SHA256";

    private final AwsCredentialsProvider credentialsProvider;
    private final ImageProperties imageProperties;
    private final ObjectMapper objectMapper;

    PostForm createForm(String key, String contentType, Instant now, Duration signatureDuration) {
        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        String base64Policy = createBase64Policy(credentials, key, contentType, now, signatureDuration);
        String signature = calculateSignature(credentials, base64Policy, now);
        Map<String, String> uploadFields = buildUploadFields(
                credentials, key, contentType, now, base64Policy, signature
        );
        return new PostForm(buildS3EndpointUrl(), uploadFields);
    }

    record PostForm(String endpointUrl, Map<String, String> uploadFields) {
    }

    private String createBase64Policy(
            AwsCredentials credentials,
            String key,
            String contentType,
            Instant now,
            Duration signatureDuration
    ) {
        String bucket = imageProperties.s3().bucket();
        String region = imageProperties.s3().region();
        long maxBytes = imageProperties.maxBytes();

        String credentialStr = buildCredentialString(credentials.accessKeyId(), formatUtcDate(now), region);
        String expirationStr = ISO_EXPIRATION_FORMATTER.format(now.plus(signatureDuration));
        String sessionToken = credentials instanceof AwsSessionCredentials sessionCredentials
                ? sessionCredentials.sessionToken() : null;

        Map<String, Object> policyMap = Map.of(
                "expiration", expirationStr,
                "conditions", buildPolicyConditions(
                        bucket, key, contentType, credentialStr, formatUtcDateTime(now), sessionToken, maxBytes
                )
        );

        return Base64.getEncoder().encodeToString(serializeJson(policyMap).getBytes(StandardCharsets.UTF_8));
    }

    private List<Object> buildPolicyConditions(
            String bucket,
            String key,
            String contentType,
            String credentialStr,
            String dateTimeStr,
            String sessionToken,
            long maxBytes
    ) {
        return Stream.of(
                condition("bucket", bucket),
                condition("key", key),
                condition(HttpHeaders.CONTENT_TYPE, contentType),
                condition(S3_TAGGING_FIELD, issuedUploadTaggingXml()),
                List.of("content-length-range", 1, maxBytes),
                condition("x-amz-algorithm", AWS_ALGORITHM),
                condition("x-amz-credential", credentialStr),
                condition("x-amz-date", dateTimeStr),
                isNotEmpty(sessionToken) ? condition("x-amz-security-token", sessionToken) : null
        ).filter(Objects::nonNull).toList();
    }

    private Map<String, String> condition(String key, String value) {
        return Map.of(key, value);
    }

    private String calculateSignature(AwsCredentials credentials, String base64Policy, Instant now) {
        String dateStr = formatUtcDate(now);
        String region = imageProperties.s3().region();

        byte[] kDate = hmacSha256(("AWS4" + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8), dateStr);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, "s3");
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        return bytesToHex(hmacSha256(kSigning, base64Policy));
    }

    private Map<String, String> buildUploadFields(
            AwsCredentials credentials,
            String key,
            String contentType,
            Instant now,
            String base64Policy,
            String signature
    ) {
        String region = imageProperties.s3().region();
        String credentialStr = buildCredentialString(credentials.accessKeyId(), formatUtcDate(now), region);

        Map<String, String> uploadFields = new LinkedHashMap<>();
        uploadFields.put("key", key);
        uploadFields.put(HttpHeaders.CONTENT_TYPE, contentType);
        uploadFields.put(S3_TAGGING_FIELD, issuedUploadTaggingXml());
        uploadFields.put("x-amz-algorithm", AWS_ALGORITHM);
        uploadFields.put("x-amz-credential", credentialStr);
        uploadFields.put("x-amz-date", formatUtcDateTime(now));

        if (credentials instanceof AwsSessionCredentials sessionCredentials
                && isNotEmpty(sessionCredentials.sessionToken())) {
            uploadFields.put("x-amz-security-token", sessionCredentials.sessionToken());
        }

        uploadFields.put("policy", base64Policy);
        uploadFields.put("x-amz-signature", signature);
        return uploadFields;
    }

    private String buildCredentialString(String accessKeyId, String dateStr, String region) {
        return accessKeyId + "/" + dateStr + "/" + region + "/s3/aws4_request";
    }

    private String serializeJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.isBlank();
    }

    private String formatUtcDate(Instant instant) {
        return UTC_DATE_FORMATTER.format(instant);
    }

    private String formatUtcDateTime(Instant instant) {
        return UTC_DATETIME_FORMATTER.format(instant);
    }

    private String buildS3EndpointUrl() {
        String publicBaseUrl = imageProperties.s3().publicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl;
        }
        return "https://" + imageProperties.s3().bucket() + ".s3."
                + imageProperties.s3().region() + ".amazonaws.com";
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private String issuedUploadTaggingXml() {
        return "<Tagging><TagSet><Tag><Key>" + UPLOAD_STATUS_TAG_KEY
                + "</Key><Value>" + UPLOAD_STATUS_ISSUED + "</Value></Tag></TagSet></Tagging>";
    }
}
