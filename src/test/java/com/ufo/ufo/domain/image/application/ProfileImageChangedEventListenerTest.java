package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import com.ufo.ufo.domain.user.event.ProfileImageChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 이미지 변경 이벤트 리스너 테스트")
class ProfileImageChangedEventListenerTest {

    @Mock
    private ProfileImageTaskRepository taskRepository;

    @Mock
    private ImageKeyPolicy keyPolicy;

    @InjectMocks
    private ProfileImageChangedEventListener listener;

    @Test
    @DisplayName("커밋 전 이미지 변경 이벤트를 받으면 태그 변경과 삭제 작업을 저장해야 한다")
    void recordTasks_SavesLinkAndDeleteTasks() {
        ProfileImageChangedEvent event =
                new ProfileImageChangedEvent(1L, "profiles/1/old-avatar", "profiles/1/new-avatar");
        when(keyPolicy.shouldDeletePreviousProfileImage(event.previousImageKey(), event.newImageKey()))
                .thenReturn(true);

        listener.recordTasks(event);

        ArgumentCaptor<ProfileImageTask> captor = ArgumentCaptor.forClass(ProfileImageTask.class);
        verify(taskRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(task -> assertThat(task.getUserId()).isEqualTo(1L));
        assertThat(captor.getAllValues()).extracting(ProfileImageTask::getTaskType)
                .containsExactly(ProfileImageTask.Type.LINK, ProfileImageTask.Type.DELETE);
        assertThat(captor.getAllValues()).extracting(ProfileImageTask::getImageKey)
                .containsExactly("profiles/1/new-avatar", "profiles/1/old-avatar");
    }
}
