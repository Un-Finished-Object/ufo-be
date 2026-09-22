package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dao.PatternAlternativeYarnRepository;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.request.CreateAlternativeRequest;
import com.ufo.ufo.domain.pattern.dto.request.UpdateAlternativeYarnRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternAlternativeResponse;
import com.ufo.ufo.domain.pattern.exception.AlternativeYarnNotFoundException;
import com.ufo.ufo.domain.pattern.exception.PatternAlternativePermissionDeniedException;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternAlternativeService {

    private final PatternRepository patternRepository;
    private final PatternAlternativeYarnRepository patternAlternativeYarnRepository;
    private final YarnRepository yarnRepository;

    @Transactional
    public PatternAlternativeResponse createAlternative(User user, Long patternId, CreateAlternativeRequest request) {
        validateAlternativePermission(user);
        Pattern pattern = findActivePattern(patternId);
        Yarn yarn = yarnRepository.save(Yarn.builder()
                .name(request.yarnName())
                .vendor(request.store())
                .price(request.cost())
                .weightG(request.weight())
                .length(request.length())
                .mainComponent(request.mainComponent())
                .subComponent(request.subComponent())
                .thickness(request.thickness())
                .build());
        PatternAlternativeYarn alternative = patternAlternativeYarnRepository.save(PatternAlternativeYarn.builder()
                .pattern(pattern)
                .user(user)
                .yarn(yarn)
                .build());
        return PatternAlternativeResponse.from(alternative);
    }

    @Transactional
    public PatternAlternativeResponse updateAlternative(
            User user,
            Long patternId,
            Long altId,
            UpdateAlternativeYarnRequest request
    ) {
        validateAlternativePermission(user);
        findActivePattern(patternId);
        PatternAlternativeYarn alternative = findAlternativeYarn(altId, patternId);
        validateAlternativeOwner(user, alternative);

        Yarn yarn = alternative.getYarn();
        yarn.update(
                request.yarnName(),
                request.store(),
                request.cost(),
                request.weight(),
                request.length(),
                request.mainComponent(),
                request.subComponent(),
                request.thickness()
        );
        alternative.update(yarn);
        return PatternAlternativeResponse.from(alternative);
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

    private PatternAlternativeYarn findAlternativeYarn(Long altId, Long patternId) {
        return patternAlternativeYarnRepository.findByIdAndPattern_Id(altId, patternId)
                .orElseThrow(AlternativeYarnNotFoundException::new);
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
}
