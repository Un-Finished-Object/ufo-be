package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.PatternViewLogRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import com.ufo.ufo.domain.pattern.dto.response.PatternViewCountResponse;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UnauthorizedUserException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternViewService {

    private final PatternRepository patternRepository;
    private final PatternViewLogRepository patternViewLogRepository;
    private final UserService userService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PatternViewCountResponse increaseViewCount(User user, Long patternId) {
        validateLoginUser(user);
        // 구매 및 보상 처리와 같은 순서로 사용자, 도안을 잠근다.
        User loginUser = userService.getUserByIdForUpdate(user.getId());
        Pattern pattern = findActivePattern(patternId);
        LocalDate today = LocalDate.now();

        if (!patternViewLogRepository.existsByPattern_IdAndUser_IdAndViewedDate(patternId, loginUser.getId(), today)) {
            pattern.increaseViewCount();
            patternViewLogRepository.save(PatternViewLog.builder()
                    .pattern(pattern)
                    .user(loginUser)
                    .viewedDate(today)
                    .build());
        }

        return PatternViewCountResponse.from(pattern.getViewCount());
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findByIdForUpdate(patternId)
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
