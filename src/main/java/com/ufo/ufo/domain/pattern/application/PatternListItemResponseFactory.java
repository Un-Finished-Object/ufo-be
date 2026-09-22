package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatternListItemResponseFactory {

    private final ScrapRepository scrapRepository;
    private final ImageService imageService;

    public PatternListItemResponse create(Pattern pattern, User user) {
        boolean scrapped = isScrapped(user, pattern.getId());
        String thumbnailUrl = imageService.buildImageUrl(pattern.getThumbnailUrl());
        return PatternListItemResponse.from(pattern, scrapped, thumbnailUrl);
    }

    private boolean isScrapped(User user, Long patternId) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), patternId);
    }
}
