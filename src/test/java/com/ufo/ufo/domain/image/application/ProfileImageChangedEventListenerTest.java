package com.ufo.ufo.domain.image.application;

import static org.mockito.Mockito.verify;

import com.ufo.ufo.domain.user.event.ProfileImageChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 이미지 변경 이벤트 리스너 테스트")
class ProfileImageChangedEventListenerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ProfileImageChangedEventListener listener;

    @Test
    @DisplayName("커밋 후 이미지 변경 이벤트를 받으면 이미지 교체 완료 처리를 수행해야 한다")
    void handle_CompletesProfileImageReplacement() {
        ProfileImageChangedEvent event =
                new ProfileImageChangedEvent("profiles/1/old-avatar", "profiles/1/new-avatar");

        listener.handle(event);

        verify(imageService).completeProfileImageReplacement("profiles/1/new-avatar", "profiles/1/old-avatar");
    }
}
