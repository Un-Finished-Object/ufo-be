package com.ufo.ufo.domain.pattern.dto.request;

import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.exception.InvalidPatternPurchaseTypeException;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PatternPurchaseRequest(
        @NotBlank(message = "type 필드의 정보가 올바르지 않습니다.")
        String type
) {
    public List<UnlockType> toUnlockTypes() {
        return switch (type.trim()) {
            case "chat" -> List.of(UnlockType.CHAT);
            case "yarn" -> List.of(UnlockType.YARN_INFO);
            default -> throw new InvalidPatternPurchaseTypeException();
        };
    }
}
