package com.ufo.ufo.domain.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PurchasedProjectsResponse(
        List<Project> projects,
        int nextPage
) {
    public static PurchasedProjectsResponse from(List<Project> projects, int nextPage) {
        return new PurchasedProjectsResponse(projects, nextPage);
    }

    public record Project(
            Long patternId,
            String patternName,
            String author,
            String thumbnailUrl,
            boolean purchaseYarn,
            LocalDateTime purchaseYarnDate,
            boolean purchaseChat,
            Long purchaseChatId,
            LocalDateTime purchaseChatDate
    ) {
    }
}
