package com.ufo.ufo.domain.user.dao.projection;

import java.time.LocalDateTime;

public interface PurchasedProjectSummary {

    Long getPatternId();

    String getPatternName();

    String getAuthor();

    String getThumbnailKey();

    Long getYarnPurchaseId();

    LocalDateTime getPurchaseYarnDate();

    Long getPurchaseChatId();

    LocalDateTime getPurchaseChatDate();
}
