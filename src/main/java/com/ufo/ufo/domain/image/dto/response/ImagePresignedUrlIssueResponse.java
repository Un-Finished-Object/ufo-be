package com.ufo.ufo.domain.image.dto.response;

import java.util.List;

public record ImagePresignedUrlIssueResponse(
        String expiresAt,
        Long maxBytes,
        List<String> allowedContentTypes,
        List<UrlInfo> urls
) {
    public record UrlInfo(
            String presignedUrl,
            String imageKey,
            String imageUrl
    ) {
        public static UrlInfo from(String presignedUrl, String imageKey, String imageUrl) {
            return new UrlInfo(presignedUrl, imageKey, imageUrl);
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
