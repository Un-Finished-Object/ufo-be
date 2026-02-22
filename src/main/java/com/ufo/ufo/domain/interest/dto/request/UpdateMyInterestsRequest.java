package com.ufo.ufo.domain.interest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMyInterestsRequest(
        @NotNull
        @Size(max = 20)
        List<@Size(min = 1, max = 50) String> keywords
) {
}
