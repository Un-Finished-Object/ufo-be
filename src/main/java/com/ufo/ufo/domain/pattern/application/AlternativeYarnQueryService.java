package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternOriginalYarnRepository;
import com.ufo.ufo.domain.pattern.dao.YarnAlternativeRepository;
import com.ufo.ufo.domain.pattern.domain.PatternOriginalYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.response.YarnAlternativesResponse;
import com.ufo.ufo.domain.pattern.exception.AlternativeYarnAccessDeniedException;
import com.ufo.ufo.domain.pattern.exception.OriginalYarnSetNotFoundException;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlternativeYarnQueryService {

    private static final int ALTERNATIVE_LIMIT = 15;

    private final PatternOriginalYarnRepository patternOriginalYarnRepository;
    private final YarnAlternativeRepository yarnAlternativeRepository;
    private final CreditService creditService;

    public YarnAlternativesResponse getAlternatives(User user, Long originalYarnSetId) {
        PatternOriginalYarn originalYarnSet = patternOriginalYarnRepository.findActiveSetById(originalYarnSetId)
                .orElseThrow(OriginalYarnSetNotFoundException::new);
        Long patternId = originalYarnSet.getPattern().getId();
        validateYarnInfoUnlocked(user, patternId);
        return new YarnAlternativesResponse(
                originalYarnSetId,
                findAlternatives(originalYarnSet.getMainYarn()),
                findAlternatives(originalYarnSet.getSecondYarn()),
                findAlternatives(originalYarnSet.getSubYarn())
        );
    }

    private List<YarnAlternativesResponse.Item> findAlternatives(Yarn originalYarn) {
        if (originalYarn == null) {
            return List.of();
        }
        return yarnAlternativeRepository.findAllByOriginalYarnId(originalYarn.getYarnId()).stream()
                .limit(ALTERNATIVE_LIMIT)
                .map(YarnAlternativesResponse.Item::from)
                .toList();
    }

    private void validateYarnInfoUnlocked(User user, Long patternId) {
        if (user == null || user.getId() == null
                || !creditService.isUnlocked(user.getId(), patternId, UnlockType.YARN_INFO)) {
            throw new AlternativeYarnAccessDeniedException();
        }
    }
}
