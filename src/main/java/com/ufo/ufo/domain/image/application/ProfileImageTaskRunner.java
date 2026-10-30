package com.ufo.ufo.domain.image.application;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.dao.projection.ProfileImageTaskId;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileImageTaskRunner {

    private final ProfileImageTaskRepository taskRepository;
    private final ProfileImageTaskProcessor processor;

    @Scheduled(fixedDelayString = "${app.image.tasks.delay-ms:30000}",
            initialDelayString = "${app.image.tasks.delay-ms:30000}")
    public void retryPendingTasks() {
        List<Long> taskIds = taskRepository
                .findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                        LocalDateTime.now(), PageRequest.of(0, 50))
                .stream()
                .map(ProfileImageTaskId::getId)
                .toList();
        process(taskIds);
    }

    private void process(List<Long> taskIds) {
        for (Long taskId : taskIds) {
            try {
                processor.process(taskId);
            } catch (RuntimeException failure) {
                log.warn("프로필 이미지 작업 실행 실패: taskId={}\n{}", taskId, ErrorLogSupport.stackTrace(failure));
            }
        }
    }
}
