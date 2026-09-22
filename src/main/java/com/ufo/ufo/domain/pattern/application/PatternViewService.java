package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.PatternViewLogRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import com.ufo.ufo.domain.pattern.dto.response.PatternViewCountResponse;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UnauthorizedUserException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternViewService {

    private final PatternRepository patternRepository;
    private final PatternViewLogRepository patternViewLogRepository;

    @Transactional
    public PatternViewCountResponse increaseViewCount(User user, Long patternId) {
        validateLoginUser(user);
        Pattern pattern = findActivePattern(patternId);
        LocalDate today = LocalDate.now();

        if (!patternViewLogRepository.existsByPattern_IdAndUser_IdAndViewedDate(patternId, user.getId(), today)) {
            pattern.increaseViewCount();
            patternViewLogRepository.save(PatternViewLog.builder()
                    .pattern(pattern)
                    .user(user)
                    .viewedDate(today)
                    .build());
        }

        return PatternViewCountResponse.from(pattern.getViewCount());
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
        return pattern;
    }

    private void validateLoginUser(User user) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedUserException();
        }
    }
}
