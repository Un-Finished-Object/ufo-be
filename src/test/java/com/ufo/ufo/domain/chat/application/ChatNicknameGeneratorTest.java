package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.chat.exception.ChatNicknameGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("채팅 닉네임 생성기 테스트")
class ChatNicknameGeneratorTest {

    private final ChatNicknameGenerator chatNicknameGenerator = new ChatNicknameGenerator();

    @Test
    @DisplayName("채팅방 상태 개수에 해당하는 컬러와 실 닉네임을 생성해야 한다")
    void generate_WithRoomStatusCount_ReturnsNicknameAtCountIndex() {
        String nickname = chatNicknameGenerator.generate(0);

        assertThat(nickname).isEqualTo("레드 코튼");
    }

    @Test
    @DisplayName("상태 개수가 증가하면 다음 닉네임을 생성해야 한다")
    void generate_WithNextRoomStatusCount_ReturnsNextNickname() {
        String firstNickname = chatNicknameGenerator.generate(0);
        String secondNickname = chatNicknameGenerator.generate(1);

        assertThat(firstNickname).isEqualTo("레드 코튼");
        assertThat(secondNickname).isEqualTo("버건디 린넨");
    }

    @Test
    @DisplayName("마지막 상태 개수에는 마지막 닉네임을 생성해야 한다")
    void generate_WithLastAvailableCount_ReturnsLastNickname() {
        String nickname = chatNicknameGenerator.generate(557);

        assertThat(nickname).isEqualTo("피치 글리터");
    }

    @Test
    @DisplayName("상태 개수가 닉네임 목록 크기 이상이면 전용 예외가 발생해야 한다")
    void generate_WhenRoomStatusCountReachesNicknameCount_ThrowsException() {
        assertThatThrownBy(() -> chatNicknameGenerator.generate(558))
                .isInstanceOf(ChatNicknameGenerationException.class);
    }
}
