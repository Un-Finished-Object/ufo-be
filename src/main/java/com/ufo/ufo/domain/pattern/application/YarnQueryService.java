package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.pattern.dao.YarnRepository;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.dto.response.YarnResponse;
import com.ufo.ufo.domain.pattern.exception.YarnNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YarnQueryService {

    private final YarnRepository yarnRepository;

    public YarnResponse getYarnDetail(Long yarnId) {
        Yarn yarn = yarnRepository.findByYarnIdAndDeletedAtIsNull(yarnId)
                .orElseThrow(YarnNotFoundException::new);
        return YarnResponse.from(yarn);
    }
}
