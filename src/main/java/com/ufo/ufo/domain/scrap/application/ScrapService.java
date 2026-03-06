package com.ufo.ufo.domain.scrap.application;

import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.scrap.domain.Scrap;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse.Item;
import com.ufo.ufo.domain.scrap.dto.response.PatternScrapResponse;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

    private final PatternRepository patternRepository;
    private final ScrapRepository scrapRepository;

    @Transactional
    public PatternScrapResponse addPatternScrap(User user, Long patternId) {
        Pattern pattern = findActivePattern(patternId);
        if (scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), patternId)) {
            return PatternScrapResponse.from(true, pattern.getScrapsCount());
        }
        scrapRepository.save(Scrap.builder()
                .user(user)
                .pattern(pattern)
                .build());
        pattern.increaseScrapsCount();
        return PatternScrapResponse.from(true, pattern.getScrapsCount());
    }

    @Transactional
    public PatternScrapResponse removePatternScrap(User user, Long patternId) {
        Pattern pattern = findActivePattern(patternId);
        scrapRepository.findByUser_IdAndPattern_Id(user.getId(), patternId)
                .ifPresent(scrap -> {
                    scrapRepository.delete(scrap);
                    pattern.decreaseScrapsCount();
                });
        return PatternScrapResponse.from(false, pattern.getScrapsCount());
    }

    public MyScrapsResponse getMyScraps(User user) {
        List<Item> scraps = scrapRepository.findAllPatternsByUserIdOrderByLatest(user.getId())
                .stream()
                .map(Scrap::getPattern)
                .map(MyScrapsResponse.Item::from)
                .toList();
        return MyScrapsResponse.from(scraps);
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
        return pattern;
    }
}
