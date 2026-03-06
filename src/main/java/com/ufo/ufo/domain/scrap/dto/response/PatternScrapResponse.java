package com.ufo.ufo.domain.scrap.dto.response;

public record PatternScrapResponse(
        boolean scrapped,
        int scrapCount
) {
    public static PatternScrapResponse from(boolean scrapped, int scrapCount) {
        return new PatternScrapResponse(scrapped, scrapCount);
    }
}
