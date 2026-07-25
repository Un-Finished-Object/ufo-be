package com.ufo.ufo.domain.image.dto.response;

import java.util.List;
import java.util.Map;

public record ImagePresignedUrlIssueResponse(
        String expiresAt,
        Long maxBytes,
        List<String> allowedContentTypes,
        List<UrlInfo> urls
) {
    public record UrlInfo(
            String presignedUrl,
            String imageKey,
            String imageUrl,
            Map<String, String> uploadFields
    ) {
        public static UrlInfo from(
                String presignedUrl,
                String imageKey,
                String imageUrl,
                Map<String, String> uploadFields
        ) {
            return new UrlInfo(presignedUrl, imageKey, imageUrl, uploadFields);
        }
    }

    public static ImagePresignedUrlIssueResponse from(
            String expiresAt,
            Long maxBytes,
            List<String> allowedContentTypes,
            List<UrlInfo> urls
    ) {
        return new ImagePresignedUrlIssueResponse(expiresAt, maxBytes, allowedContentTypes, urls);
    }
}
