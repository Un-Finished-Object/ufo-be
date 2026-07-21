package com.ufo.ufo.domain.referral.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.referral.ReferralCodeProperties;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("친구 초대 코드 HMAC 생성기 테스트")
class ReferralCodeGeneratorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("같은 User ID와 nonce에는 같은 9자리 코드를 생성해야 한다")
    void generate_WithSameInput_ReturnsSameCode() {
        ReferralCodeGenerator generator = generator(SECRET);

        String first = generator.generate(1L, 0);
        String second = generator.generate(1L, 0);

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("UFO[0-9A-Za-z]{6}");
    }

    @Test
    @DisplayName("비밀키가 다르면 같은 User ID도 다른 코드를 생성해야 한다")
    void generate_WithDifferentSecret_ReturnsDifferentCode() {
        ReferralCodeGenerator firstGenerator = generator(SECRET);
        ReferralCodeGenerator secondGenerator = generator("abcdef0123456789abcdef0123456789");

        assertThat(firstGenerator.generate(1L, 0))
                .isNotEqualTo(secondGenerator.generate(1L, 0));
    }

    @Test
    @DisplayName("비밀키가 충분히 길지 않으면 전용 예외가 발생해야 한다")
    void generate_WithShortSecret_ThrowsException() {
        ReferralCodeGenerator generator = generator("short-secret");

        assertThatThrownBy(() -> generator.generate(1L, 0))
                .isInstanceOf(ReferralCodeGenerationException.class);
    }

    private ReferralCodeGenerator generator(String secret) {
        return new ReferralCodeGenerator(new ReferralCodeProperties(secret));
    }
}
