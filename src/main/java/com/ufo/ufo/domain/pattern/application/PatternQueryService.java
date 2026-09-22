package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternImage;
import com.ufo.ufo.domain.pattern.domain.PatternSort;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternSubCategoryNotAllowedException;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternQueryService {

    private static final int PAGE_SIZE = 20;

    private final PatternRepository patternRepository;
    private final PatternImageRepository patternImageRepository;
    private final ScrapRepository scrapRepository;
    private final ImageService imageService;
    private final PatternListItemResponseFactory responseFactory;

    public PatternListResponse getPatterns(User user, String category, String subCategory, String sort, Integer page) {
        validateCategoryAndSubCategory(category, subCategory);
        PatternSort sortOption = PatternSort.from(sort);
        int pageNumber = normalizePage(page);
        PageRequest pageRequest = createPageRequestForSort(sortOption, pageNumber);
        String categoryFilter = normalizeCategoryFilter(category);
        String subCategoryFilter = normalizeCategoryFilter(subCategory);

        Page<Pattern> result = sortOption == PatternSort.SCRAPS
                ? patternRepository.findAllByCategoryOrderByPopularity(categoryFilter, subCategoryFilter, pageRequest)
                : patternRepository.findAllByCategory(categoryFilter, subCategoryFilter, pageRequest);

        int nextPage = resolveNextPage(pageNumber, result.getTotalPages());
        return PatternListResponse.from(
                result.stream().map(pattern -> responseFactory.create(pattern, user)).toList(),
                pageNumber,
                nextPage
        );
    }

    public PatternListResponse searchPatterns(User user, String keyword, Integer page) {
        int pageNumber = normalizePage(page);
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Pattern> result = patternRepository.search(keyword == null ? "" : keyword, pageRequest);
        int nextPage = resolveNextPage(pageNumber, result.getTotalPages());
        return PatternListResponse.from(
                result.stream().map(pattern -> responseFactory.create(pattern, user)).toList(),
                pageNumber,
                nextPage
        );
    }

    public PatternDetailResponse getPatternDetail(User user, Long patternId) {
        Pattern pattern = patternRepository.findDetailById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        boolean scrapped = isScrapped(user, patternId);
        List<String> images = resolvePatternImages(patternId, pattern.getThumbnailUrl());
        return PatternDetailResponse.from(pattern, images, scrapped);
    }

    private List<String> resolvePatternImages(Long patternId, String thumbnailUrl) {
        List<String> images = patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(patternId)
                .stream()
                .map(PatternImage::getImageUrl)
                .map(imageService::buildImageUrl)
                .toList();
        if (images.isEmpty() && thumbnailUrl != null) {
            return List.of(imageService.buildImageUrl(thumbnailUrl));
        }
        return images;
    }

    private boolean isScrapped(User user, Long patternId) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), patternId);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private String normalizeCategoryFilter(String category) {
        if (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) {
            return null;
        }
        return category;
    }

    private void validateCategoryAndSubCategory(String category, String subCategory) {
        if (!"apparel".equalsIgnoreCase(category) && subCategory != null) {
            throw new PatternSubCategoryNotAllowedException();
        }
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        return remainingPages <= 0 ? 0 : Math.min(remainingPages, 5);
    }

    private PageRequest createPageRequestForSort(PatternSort sort, int pageNumber) {
        if (sort == PatternSort.SCRAPS) {
            return PageRequest.of(pageNumber - 1, PAGE_SIZE);
        }
        if (sort == PatternSort.VIEWS) {
            return PageRequest.of(pageNumber - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "viewCount"));
        }
        return PageRequest.of(pageNumber - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
