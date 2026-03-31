package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.PatternImage;
import com.ufo.ufo.domain.pattern.domain.PatternSort;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativesResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternDetailResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListItemResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternListResponse;
import com.ufo.ufo.domain.pattern.exception.AlternativeYarnNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternAlternativePermissionDeniedException;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternSubCategoryNotAllowedException;
import com.ufo.ufo.domain.pattern.exception.PatternSubCategoryRequiredException;
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
public class PatternService {

    private static final int PAGE_SIZE = 20;

    private final PatternRepository patternRepository;
    private final ScrapRepository scrapRepository;
    private final PatternImageRepository patternImageRepository;
    private final PatternAlternativeYarnRepository patternAlternativeYarnRepository;
    private final YarnRepository yarnRepository;

    public PatternListResponse getPatterns(User user, String category, String subCategory, String sort, Integer page) {
        validateCategoryAndSubCategory(category, subCategory);
        PatternSort sortOption = PatternSort.from(sort);
        int pageNumber = normalizePage(page);
        PageRequest pageRequest = createPageRequestForSort(sortOption, pageNumber);
        String categoryFilter = normalizeCategoryFilter(category);
        String subCategoryFilter = normalizeCategoryFilter(subCategory);

        Page<Pattern> result = isScrapsSort(sortOption)
                ? patternRepository.findAllByCategoryOrderByPopularity(categoryFilter, subCategoryFilter, pageRequest)
                : patternRepository.findAllByCategory(categoryFilter, subCategoryFilter, pageRequest);

        int nextPage = resolveNextPage(pageNumber, result.getTotalPages());
        return PatternListResponse.from(result.stream().map(pattern -> toListItemResponse(pattern, user)).toList(), pageNumber, nextPage);
    }

    public PatternItemsResponse getRecommendedPatterns(User user) {
        List<Pattern> result = findRecommendedPatternsByUserInterest(user);
        return new PatternItemsResponse(result.stream().map(pattern -> toListItemResponse(pattern, user)).toList());
    }

    public PatternListResponse searchPatterns(User user, String keyword, Integer page) {
        int pageNumber = normalizePage(page);
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Pattern> result = patternRepository.search(keyword == null ? "" : keyword, pageRequest);
        int nextPage = resolveNextPage(pageNumber, result.getTotalPages());
        return PatternListResponse.from(result.stream().map(pattern -> toListItemResponse(pattern, user)).toList(), pageNumber, nextPage);
    }

    @Transactional
    public PatternDetailResponse getPatternDetail(User user, Long patternId) {
        Pattern pattern = findActivePattern(patternId);
        pattern.increaseViewCount();
        boolean isScrapped = scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), patternId);
        List<String> images = resolvePatternImages(patternId, pattern.getThumbnailUrl());
        return PatternDetailResponse.from(pattern, images, isScrapped);
    }

    @Transactional
    public PatternAlternativesResponse getAlternatives(User user, Long patternId) {
        findActivePattern(patternId);
        return PatternAlternativesResponse.fromAlternatives(
                patternAlternativeYarnRepository.findAllByPattern_IdOrderByIdAsc(patternId)
        );
    }

    @Transactional
    public PatternAlternativesResponse.Item createAlternative(User user, Long patternId, CreateAlternativeRequest request) {
        validateAlternativePermission(user);
        Pattern pattern = findActivePattern(patternId);
        Yarn yarn = createAndSaveYarn(request);
        PatternAlternativeYarn alternative = createAndSaveAlternativeYarn(pattern, user, yarn, request);
        return PatternAlternativesResponse.Item.from(alternative);
    }

    @Transactional
    public PatternAlternativesResponse.Item updateAlternative(User user, Long patternId, Long altId, UpdateAlternativeYarnRequest request) {
        validateAlternativePermission(user);
        findActivePattern(patternId);
        PatternAlternativeYarn alternative = findAlternativeYarn(altId, patternId);
        validateAlternativeOwner(user, alternative);

        updateAlternativeYarn(alternative, request);
        return PatternAlternativesResponse.Item.from(alternative);
    }

    @Transactional
    public void deleteAlternative(User user, Long patternId, Long altId) {
        validateAlternativePermission(user);
        findActivePattern(patternId);
        PatternAlternativeYarn alternative = findAlternativeYarn(altId, patternId);
        validateAlternativeOwner(user, alternative);
        patternAlternativeYarnRepository.delete(alternative);
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
        return pattern;
    }

    private void validateAlternativePermission(User user) {
        if (user.isGuest()) {
            throw new PatternAlternativePermissionDeniedException();
        }
    }

    private void validateAlternativeOwner(User user, PatternAlternativeYarn alternative) {
        if (!alternative.isOwnedBy(user)) {
            throw new PatternAlternativePermissionDeniedException();
        }
    }

    private List<String> resolvePatternImages(Long patternId, String thumbnailUrl) {
        List<String> images = patternImageRepository.findAllByPattern_IdOrderByImageOrderAscIdAsc(patternId)
                .stream()
                .map(PatternImage::getImageUrl)
                .toList();
        if (images.isEmpty() && thumbnailUrl != null) {
            return List.of(thumbnailUrl);
        }
        return images;
    }

    private Yarn createAndSaveYarn(CreateAlternativeRequest request) {
        return yarnRepository.save(Yarn.builder()
                .name(request.yarnName())
                .vendor(request.store())
                .price(request.cost())
                .weightG(request.weight())
                .length(null)
                .ingredient(null)
                .thickness(null)
                .build());
    }

    private PatternAlternativeYarn createAndSaveAlternativeYarn(
            Pattern pattern,
            User user,
            Yarn yarn,
            CreateAlternativeRequest request
    ) {
        return patternAlternativeYarnRepository.save(PatternAlternativeYarn.builder()
                .pattern(pattern)
                .user(user)
                .yarn(yarn)
                .gauge(request.gauge())
                .imageUrl(request.yarnUri())
                .build());
    }

    private PatternAlternativeYarn findAlternativeYarn(Long altId, Long patternId) {
        return patternAlternativeYarnRepository.findByIdAndPattern_Id(altId, patternId)
                .orElseThrow(AlternativeYarnNotFoundException::new);
    }

    private void updateAlternativeYarn(PatternAlternativeYarn alternative, UpdateAlternativeYarnRequest request) {
        Yarn yarn = alternative.getYarn();
        yarn.update(
                request.yarnName(),
                request.store(),
                request.cost(),
                request.weight(),
                yarn.getLength(),
                yarn.getIngredient(),
                yarn.getThickness()
        );
        alternative.update(yarn, request.gauge(), request.yarnUri());
    }

    private boolean isScrapsSort(PatternSort sort) {
        return sort == PatternSort.SCRAPS;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private String normalizeCategoryFilter(String category) {
        if (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) {
            return null;
        }
        return category;
    }

    private void validateCategoryAndSubCategory(String category, String subCategory) {
        if ("apparel".equalsIgnoreCase(category) && subCategory == null) {
            throw new PatternSubCategoryRequiredException();
        }
        if (!"apparel".equalsIgnoreCase(category) && subCategory != null) {
            throw new PatternSubCategoryNotAllowedException();
        }
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        if (remainingPages <= 0) {
            return 0;
        }
        return Math.min(remainingPages, 5);
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

    private PatternListItemResponse toListItemResponse(Pattern pattern, User user) {
        boolean scrapped = scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), pattern.getId());
        return PatternListItemResponse.from(pattern, scrapped);
    }

    private List<Pattern> findRecommendedPatternsByUserInterest(User user) {
        List<Pattern> interestMatched = patternRepository.findRecommendedByUserInterest(user.getId());
        if (!interestMatched.isEmpty()) {
            return interestMatched;
        }
        return patternRepository.findRecommended();
    }

}
