package com.ufo.ufo.domain.user.application.model;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse.Project;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurchasedProject {

    private final Pattern pattern;
    private boolean purchaseYarn;
    private LocalDateTime purchaseYarnDate;
    private boolean purchaseChat;
    private Long purchaseChatId;
    private LocalDateTime purchaseChatDate;

    public PurchasedProject(Pattern pattern) {
        this.pattern = pattern;
    }

    public Long getPatternId() {
        return pattern.getId();
    }

    public void applyYarn(LocalDateTime purchaseDate) {
        this.purchaseYarn = true;
        this.purchaseYarnDate = purchaseDate;
    }

    public void applyChat(Long chatRoomId, LocalDateTime purchaseDate) {
        if (purchaseChatDate != null && !purchaseDate.isAfter(purchaseChatDate)) {
            return;
        }
        this.purchaseChat = true;
        this.purchaseChatId = chatRoomId;
        this.purchaseChatDate = purchaseDate;
    }

    public LocalDateTime latestPurchaseDate() {
        List<LocalDateTime> dates = new ArrayList<>();
        if (purchaseYarnDate != null) {
            dates.add(purchaseYarnDate);
        }
        if (purchaseChatDate != null) {
            dates.add(purchaseChatDate);
        }
        return dates.stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    public Project toResponse() {
        return new Project(
                pattern.getId(),
                pattern.getTitle(),
                pattern.getDesigner(),
                pattern.getThumbnailUrl(),
                purchaseYarn,
                purchaseYarnDate,
                purchaseChat,
                purchaseChatId,
                purchaseChatDate
        );
    }
}
