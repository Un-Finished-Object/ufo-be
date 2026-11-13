package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.interest.domain.InterestKeyword;
import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.pattern.dao.PatternRecommendationRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.user.domain.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternRecommendationService {

    private static final int RECOMMENDATION_LIMIT = 30;

    private final PatternRecommendationRepository recommendationRepository;
    private final UserInterestRepository userInterestRepository;
    private final PatternListItemResponseFactory responseFactory;
    private final RandomGenerator patternRecommendationRandom;

    public PatternItemsResponse getRecommendedPatterns(User user) {
        List<Pattern> result = findRecommendedPatterns(user);
        return new PatternItemsResponse(responseFactory.createAll(result, user));
    }

    private List<Pattern> findRecommendedPatterns(User user) {
        if (user == null || user.getId() == null) {
            return recommendationRepository.findPopular(RECOMMENDATION_LIMIT);
        }
        List<Integer> interestNumbers = userInterestRepository.findAllByUser_Id(user.getId())
                .stream()
                .map(UserInterest::getKeyword)
                .flatMap(keyword -> InterestKeyword.findNumberByKeyword(keyword).stream())
                .distinct()
                .toList();
        if (interestNumbers.isEmpty()) {
            return recommendationRepository.findPopular(RECOMMENDATION_LIMIT);
        }

        List<Integer> fitKeywords = InterestKeyword.extractFitKeywords(interestNumbers);
        int candidateCount = Math.toIntExact(recommendationRepository.countMatching(interestNumbers, fitKeywords));
        if (candidateCount == 0) {
            return recommendationRepository.findPopular(RECOMMENDATION_LIMIT);
        }
        int limit = Math.min(RECOMMENDATION_LIMIT, candidateCount);
        int offset = candidateCount > limit ? patternRecommendationRandom.nextInt(candidateCount) : 0;
        List<Pattern> candidates = new ArrayList<>(
                recommendationRepository.findMatching(interestNumbers, fitKeywords, offset, limit));
        if (offset > 0 && candidates.size() < limit) {
            candidates.addAll(recommendationRepository.findMatching(
                    interestNumbers, fitKeywords, 0, limit - candidates.size()));
        }
        // 집계 이후 후보가 바뀌어 앞뒤 구간이 겹쳐도 같은 도안은 한 번만 반환한다.
        Map<Long, Pattern> uniqueCandidates = new LinkedHashMap<>();
        candidates.forEach(pattern -> uniqueCandidates.putIfAbsent(pattern.getId(), pattern));
        List<Pattern> result = new ArrayList<>(uniqueCandidates.values());
        Collections.shuffle(result);
        return result;
    }
}
