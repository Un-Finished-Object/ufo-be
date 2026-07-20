package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.interest.domain.InterestKeyword;
import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternImageRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.PatternImage;
import com.ufo.ufo.domain.pattern.domain.PatternSort;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnGauge;
import com.ufo.ufo.domain.pattern.dto.request.AlternativeGaugeRequest;
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
import com.ufo.ufo.domain.user.domain.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private static final int ALTERNATIVE_RANDOM_LIMIT = 5;

    private final PatternRepository patternRepository;
    private final ScrapRepository scrapRepository;
    private final PatternImageRepository patternImageRepository;
    private final PatternAlternativeYarnRepository patternAlternativeYarnRepository;
    private final YarnRepository yarnRepository;
    private final UserInterestRepository userInterestRepository;
    private final ImageService imageService;

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
        List<Pattern> result = findRecommendedPatterns(user);
        List<PatternListItemResponse> items = result.stream()
                .map(pattern -> toListItemResponse(pattern, user))
                .toList();
        return new PatternItemsResponse(items);
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
        boolean isScrapped = isScrapped(user, patternId);
        List<String> images = resolvePatternImages(patternId, pattern.getThumbnailUrl());
        return PatternDetailResponse.from(pattern, images, isScrapped);
    }

    @Transactional
    public PatternAlternativesResponse getAlternatives(User user, Long patternId) {
        Pattern pattern = findActivePattern(patternId);
        Optional<AlternativeFilter> alternativeFilter = resolveAlternativeFilter(pattern);
        if (alternativeFilter.isEmpty()) {
            return PatternAlternativesResponse.fromYarns(List.of());
        }
        AlternativeFilter filter = alternativeFilter.get();
        List<Yarn> yarns = new ArrayList<>(yarnRepository.findAllActiveByThicknessCategoryAndMainComponentExcludingYarnId(
                filter.thicknessCategory(),
                filter.mainComponent(),
                filter.excludedYarnId()
        ));
        Collections.shuffle(yarns);
        return PatternAlternativesResponse.fromYarns(yarns.stream().limit(ALTERNATIVE_RANDOM_LIMIT).toList());
    }

    @Transactional
    public PatternAlternativesResponse.Item createAlternative(User user, Long patternId, CreateAlternativeRequest request) {
        validateAlternativePermission(user);
        Pattern pattern = findActivePattern(patternId);
        Yarn yarn = createAndSaveYarn(request);
        PatternAlternativeYarn alternative = createAndSaveAlternativeYarn(pattern, user, yarn);
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
        patternAlternativeYarnRepository.deleteById(altId);
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
                .map(imageService::buildImageUrl)
                .toList();
        if (images.isEmpty() && thumbnailUrl != null) {
            return List.of(imageService.buildImageUrl(thumbnailUrl));
        }
        return images;
    }

    private Yarn createAndSaveYarn(CreateAlternativeRequest request) {
        return yarnRepository.save(Yarn.builder()
                .name(request.yarnName())
                .vendor(request.store())
                .price(request.cost())
                .weightG(request.weight())
                .length(request.length())
                .mainComponent(request.mainComponent())
                .subComponent(request.subComponent())
                .thickness(request.thickness())
                .thicknessCategory(null)
                .gauges(toGaugeEntities(request.gauges()))
                .build());
    }

    private PatternAlternativeYarn createAndSaveAlternativeYarn(
            Pattern pattern,
            User user,
            Yarn yarn
    ) {
        return patternAlternativeYarnRepository.save(PatternAlternativeYarn.builder()
                .pattern(pattern)
                .user(user)
                .yarn(yarn)
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
                request.length(),
                request.mainComponent(),
                request.subComponent(),
                request.thickness(),
                yarn.getThicknessCategory(),
                toGaugeEntities(request.gauges())
        );
        alternative.update(yarn);
    }

    private Optional<AlternativeFilter> resolveAlternativeFilter(Pattern pattern) {
        Optional<Yarn> originalYarn = pattern.getOriginalYarns().stream()
                .map(PatternOriginalYarn::getMainYarn)
                .findFirst();
        if (originalYarn.isEmpty()) {
            return Optional.empty();
        }
        Yarn yarn = originalYarn.get();
        if (yarn.getDeletedAt() != null || yarn.getYarnId() == null
            || yarn.getThicknessCategory() == null || yarn.getThicknessCategory().isBlank()
            || yarn.getMainComponent() == null || yarn.getMainComponent().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AlternativeFilter(
                yarn.getThicknessCategory().trim(),
                yarn.getMainComponent().trim(),
                yarn.getYarnId()
        ));
    }

    private List<YarnGauge> toGaugeEntities(List<AlternativeGaugeRequest> gauges) {
        return gauges.stream()
                .map(gauge -> YarnGauge.builder()
                        .needleSize(gauge.needleSize())
                        .stitch(gauge.stitch())
                        .rowCount(gauge.row())
                        .build())
                .toList();
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
        boolean scrapped = isScrapped(user, pattern.getId());
        return PatternListItemResponse.from(pattern, scrapped, imageService.buildImageUrl(pattern.getThumbnailUrl()));
    }

    private boolean isScrapped(User user, Long patternId) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return scrapRepository.existsByUser_IdAndPattern_Id(user.getId(), patternId);
    }

    private List<Pattern> findRecommendedPatterns(User user) {
        if (user == null || user.getId() == null) {
            return patternRepository.findRecommended();
        }
        List<Integer> interestNumbers = userInterestRepository.findAllByUser_Id(user.getId())
                .stream()
                .map(UserInterest::getKeyword)
                .flatMap(keyword -> InterestKeyword.findNumberByKeyword(keyword).stream())
                .distinct()
                .toList();
        if (interestNumbers.isEmpty()) {
            return patternRepository.findRecommended();
        }
        List<Pattern> interestMatched = patternRepository.findRecommendedByInterestNumbers(interestNumbers);
        if (!interestMatched.isEmpty()) {
            List<Pattern> shuffled = new ArrayList<>(interestMatched);
            Collections.shuffle(shuffled);
            return shuffled;
        }
        return patternRepository.findRecommended();
    }

    private record AlternativeFilter(
            String thicknessCategory,
            String mainComponent,
            Long excludedYarnId
    ) {
    }

}

