package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatternListItemResponseFactory {

    private final ScrapRepository scrapRepository;
    private final ImageService imageService;

    public List<PatternListItemResponse> createAll(List<Pattern> patterns, User user) {
        Set<Long> scrappedIds = findScrappedPatternIds(patterns, user);
        return patterns.stream()
                .map(pattern -> PatternListItemResponse.from(
                        pattern,
                        scrappedIds.contains(pattern.getId()),
                        imageService.buildImageUrl(pattern.getThumbnailUrl())))
                .toList();
    }

    private Set<Long> findScrappedPatternIds(List<Pattern> patterns, User user) {
        if (patterns.isEmpty() || user == null || user.getId() == null) {
            return Set.of();
        }
        List<Long> patternIds = patterns.stream().map(Pattern::getId).toList();
        return scrapRepository.findScrappedPatternIds(user.getId(), patternIds);
    }
}
