package com.ufo.ufo.domain.scrap.application;

import com.ufo.ufo.domain.image.application.ImageService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

    private static final int PAGE_SIZE = 20;

    private final PatternRepository patternRepository;
    private final ScrapRepository scrapRepository;
    private final ImageService imageService;

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

    public MyScrapsResponse getMyScraps(User user, Integer page) {
        int pageNumber = normalizePage(page);
        Page<Scrap> scrapPage = scrapRepository.findAllByUser_IdAndPattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(
                user.getId(),
                PageRequest.of(pageNumber - 1, PAGE_SIZE)
        );
        List<Item> scraps = scrapPage.getContent()
                .stream()
                .map(Scrap::getPattern)
                .map(pattern -> MyScrapsResponse.Item.from(pattern, imageService.buildImageUrl(pattern.getThumbnailUrl())))
                .toList();
        int nextPage = resolveNextPage(pageNumber, scrapPage.getTotalPages());
        return MyScrapsResponse.from(scraps, pageNumber, nextPage);
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
        return pattern;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        if (remainingPages <= 0) {
            return 0;
        }
        return Math.min(remainingPages, 5);
    }
}
