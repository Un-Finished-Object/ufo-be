package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternPurchaseService {

    private final PatternRepository patternRepository;
    private final CreditService creditService;

    @Transactional
    public PatternPurchaseResponse purchase(User user, Long patternId, PatternPurchaseRequest request) {
        findActivePattern(patternId);
        request.toUnlockTypes()
                .forEach(unlockType -> creditService.purchaseUnlock(user, patternId, unlockType));
        return PatternPurchaseResponse.from(user.getId(), request.type());
    }

    public PatternPurchaseStatusResponse getStatus(User user, Long patternId) {
        findActivePattern(patternId);
        return PatternPurchaseStatusResponse.from(
                user.getId(),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.CHAT),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.YARN_INFO)
        );
    }

    private void findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
    }
}
