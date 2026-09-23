package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.interest.domain.InterestKeyword;
import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternItemsResponse;
import com.ufo.ufo.domain.user.domain.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternRecommendationService {

    private final PatternRepository patternRepository;
    private final UserInterestRepository userInterestRepository;
    private final PatternListItemResponseFactory responseFactory;

    public PatternItemsResponse getRecommendedPatterns(User user) {
        List<Pattern> result = findRecommendedPatterns(user);
        return new PatternItemsResponse(result.stream()
                .map(pattern -> responseFactory.create(pattern, user))
                .toList());
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

        List<Integer> fitKeywords = InterestKeyword.extractFitKeywords(interestNumbers);
        List<Pattern> interestMatched = fitKeywords.isEmpty()
                ? patternRepository.findRecommendedByInterestNumbers(interestNumbers)
                : patternRepository.findRecommendedByInterestNumbersWithFit(interestNumbers, fitKeywords);

        if (!interestMatched.isEmpty()) {
            List<Pattern> shuffled = new ArrayList<>(interestMatched);
            Collections.shuffle(shuffled);
            return shuffled;
        }
        return patternRepository.findRecommended();
    }
}
