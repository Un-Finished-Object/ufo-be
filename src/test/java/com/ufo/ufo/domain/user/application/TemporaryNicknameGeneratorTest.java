package com.ufo.ufo.domain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.user.dao.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("임시 닉네임 생성기 테스트")
class TemporaryNicknameGeneratorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TemporaryNicknameGenerator temporaryNicknameGenerator;

    @Test
    @DisplayName("선호 닉네임이 사용 가능하면 그대로 반환해야 한다")
    void generate_WhenAvailable_ReturnsPreferredNickname() {
        when(userRepository.existsByNickname("뜨개러")).thenReturn(false);

        assertThat(temporaryNicknameGenerator.generate("뜨개러")).isEqualTo("뜨개러");
    }

    @Test
    @DisplayName("선호 닉네임이 중복되면 사용 가능한 숫자 suffix를 붙여야 한다")
    void generate_WhenDuplicated_AppendsAvailableSuffix() {
        when(userRepository.existsByNickname("뜨개러")).thenReturn(true);
        when(userRepository.existsByNickname("뜨개러#1")).thenReturn(true);
        when(userRepository.existsByNickname("뜨개러#2")).thenReturn(false);

        assertThat(temporaryNicknameGenerator.generate("뜨개러")).isEqualTo("뜨개러#2");
    }

    @Test
    @DisplayName("suffix를 붙인 임시 닉네임은 최대 20자를 넘지 않아야 한다")
    void generate_WithLongNickname_TruncatesBaseNickname() {
        String longNickname = "1234567890123456789012345";
        when(userRepository.existsByNickname("12345678901234567890")).thenReturn(true);
        when(userRepository.existsByNickname("123456789012345678#1")).thenReturn(false);

        assertThat(temporaryNicknameGenerator.generate(longNickname))
                .isEqualTo("123456789012345678#1")
                .hasSize(20);
    }
}
