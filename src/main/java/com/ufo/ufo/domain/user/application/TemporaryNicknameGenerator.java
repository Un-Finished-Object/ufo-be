package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.exception.TemporaryNicknameGenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemporaryNicknameGenerator {

    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_SUFFIX = 999_999;
    private static final String DEFAULT_NICKNAME = "사용자";

    private final UserRepository userRepository;

    public String generate(String preferredNickname) {
        String baseNickname = normalizeBaseNickname(preferredNickname);
        if (!userRepository.existsByNickname(baseNickname)) {
            return baseNickname;
        }

        for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
            String suffixText = "#" + suffix;
            int baseLength = MAX_NICKNAME_LENGTH - suffixText.length();
            String candidate = truncate(baseNickname, baseLength) + suffixText;
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new TemporaryNicknameGenerationException();
    }

    private String normalizeBaseNickname(String preferredNickname) {
        if (preferredNickname == null || preferredNickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return truncate(preferredNickname.trim(), MAX_NICKNAME_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
