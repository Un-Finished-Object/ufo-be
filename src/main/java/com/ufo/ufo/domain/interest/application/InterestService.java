package com.ufo.ufo.domain.interest.application;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.interest.domain.InterestKeyword;
import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.interest.dto.request.UpdateMyInterestsRequest;
import com.ufo.ufo.domain.interest.dto.response.InterestKeywordsResponse;
import com.ufo.ufo.domain.interest.dto.response.MyInterestsResponse;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidInterestKeywordException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestService {

    private final UserInterestRepository userInterestRepository;
    private final UserService userService;

    public InterestKeywordsResponse getInterestKeywords() {
        return new InterestKeywordsResponse(InterestKeyword.names());
    }

    public MyInterestsResponse getMyInterests(User user) {
        List<String> keywords = userInterestRepository.findAllByUser_Id(user.getId())
                .stream()
                .map(UserInterest::getKeyword)
                .toList();
        return new MyInterestsResponse(keywords);
    }

    @Transactional
    public MyInterestsResponse updateMyInterests(User user, UpdateMyInterestsRequest request) {
        User loginUser = userService.getUserById(user.getId());
        List<String> normalizedKeywords = replaceMyInterests(loginUser, request.keywords());
        return new MyInterestsResponse(normalizedKeywords);
    }

    @Transactional
    public List<String> replaceMyInterests(User user, List<String> keywords) {
        List<String> normalizedKeywords = normalizeAndValidate(keywords);
        replaceUserInterests(user, normalizedKeywords);
        return normalizedKeywords;
    }

    private void replaceUserInterests(User user, List<String> normalizedKeywords) {
        userInterestRepository.deleteAllByUser_Id(user.getId());

        List<UserInterest> userInterests = normalizedKeywords.stream()
                .map(keyword -> UserInterest.builder()
                        .user(user)
                        .keyword(keyword)
                        .build())
                .toList();
        userInterestRepository.saveAll(userInterests);
    }

    private List<String> normalizeAndValidate(List<String> keywords) {
        List<String> uniqueKeywords = keywords.stream()
                .map(keyword -> keyword == null ? null : keyword.trim())
                .filter(keyword -> keyword != null && !keyword.isEmpty())
                .map(keyword -> keyword.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<String> invalidKeywords = new ArrayList<>();
        for (String keyword : uniqueKeywords) {
            if (!InterestKeyword.isSupported(keyword)) {
                invalidKeywords.add(keyword);
            }
        }

        if (!invalidKeywords.isEmpty()) {
            throw new InvalidInterestKeywordException(invalidKeywords.getFirst());
        }
        return uniqueKeywords;
    }
}
