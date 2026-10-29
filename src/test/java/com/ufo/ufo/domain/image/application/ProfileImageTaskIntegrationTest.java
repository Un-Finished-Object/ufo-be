package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import com.ufo.ufo.domain.image.exception.ProfileImageNotUploadedException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.ProfileImageTestConfig;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, ProfileImageTestConfig.class,
        UserService.class, ImageService.class, ImageKeyPolicy.class, ImageUploadValidator.class,
        S3ImageObjectManager.class, ProfileImageChangedEventListener.class,
        ProfileImageTaskRunner.class, ProfileImageTaskProcessor.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 프로필 이미지 후처리 통합 테스트")
class ProfileImageTaskIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private ProfileImageTaskRepository taskRepository;
    @Autowired
    private ProfileImageTaskRunner runner;
    @Autowired
    private ProfileImageTaskProcessor processor;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private S3Client s3Client;
    @MockitoBean
    private S3PostPolicySigner signer;

    private User user;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAllInBatch();
        String identifier = UUID.randomUUID().toString().replace("-", "");
        user = new TransactionTemplate(transactionManager).execute(status -> {
            User created = UserFixture.createUser(identifier + "@example.test", Role.ROLE_USER);
            created.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
            entityManager.persist(created);
            return created;
        });
    }

    @Test
    @DisplayName("업로드되지 않은 이미지는 프로필에 저장하지 않아야 한다")
    void updateProfile_WithMissingObject_RollsBackProfileChanges() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> userService.updateNameAndProfileImage(user, "새닉네임", key("missing")))
                .isInstanceOf(ProfileImageNotUploadedException.class);

        assertThat(currentImage()).isEqualTo("defaults/profile.png");
        assertThat(jdbc.queryForObject("select nickname from users where user_id = ?", String.class, user.getId()))
                .isEqualTo(user.getNickname());
        assertThat(taskRepository.count()).isZero();
        verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
    }

    @Test
    @DisplayName("태그 변경 실패 작업은 새 실행기에서도 DB에서 읽어 재시도해야 한다")
    void retryPendingTasks_WithTagFailure_LoadsPersistedTask() {
        when(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenThrow(storageFailure()).thenReturn(PutObjectTaggingResponse.builder().build());

        userService.updateNameAndProfileImage(user, null, key("new"));
        runner.retryPendingTasks();

        ProfileImageTask pending = taskRepository.findAll().getFirst();
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(currentImage()).isEqualTo(key("new"));
        runner.retryPendingTasks();
        verify(s3Client).putObjectTagging(any(PutObjectTaggingRequest.class));

        makeTasksDue();
        new ProfileImageTaskRunner(taskRepository, processor).retryPendingTasks();

        assertThat(taskRepository.count()).isZero();
        verify(s3Client, times(2)).putObjectTagging(any(PutObjectTaggingRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("기존 이미지 삭제에 실패해도 새 프로필을 유지하고 삭제를 재시도해야 한다")
    void retryPendingTasks_WithDeleteFailure_RetriesPreviousImageDeletion() {
        setCurrentImage(key("old"));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(storageFailure()).thenReturn(DeleteObjectResponse.builder().build());

        userService.updateNameAndProfileImage(user, null, key("new"));
        runner.retryPendingTasks();

        assertThat(currentImage()).isEqualTo(key("new"));
        assertThat(taskRepository.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getTaskType()).isEqualTo(ProfileImageTask.Type.DELETE);
            assertThat(task.getImageKey()).isEqualTo(key("old"));
            assertThat(task.getAttempts()).isEqualTo(1);
        });
        makeTasksDue();
        runner.retryPendingTasks();

        assertThat(taskRepository.count()).isZero();
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("프로필이 커밋된 뒤에 S3 태그를 변경해야 한다")
    void updateProfile_WhenCommitted_ChangesS3AfterCommit() {
        AtomicReference<String> observedImage = new AtomicReference<>();
        when(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class))).thenAnswer(invocation -> {
            observedImage.set(currentImage());
            return PutObjectTaggingResponse.builder().build();
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userService.updateNameAndProfileImage(user, null, key("new"));
            verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
        });

        runner.retryPendingTasks();
        assertThat(observedImage.get()).isEqualTo(key("new"));
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    @DisplayName("프로필 변경이 롤백되면 작업 저장과 S3 변경도 없어야 한다")
    void updateProfile_WhenRolledBack_DiscardsPendingTasks() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userService.updateNameAndProfileImage(user, null, key("rolled-back"));
            status.setRollbackOnly();
        });

        assertThat(currentImage()).isEqualTo("defaults/profile.png");
        assertThat(taskRepository.count()).isZero();
        verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("이미지를 연속 변경한 후 재시도해도 현재 이미지를 삭제하지 않아야 한다")
    void retryPendingTasks_WithRapidChanges_PreservesCurrentImage() {
        userService.updateNameAndProfileImage(user, null, key("a"));
        userService.updateNameAndProfileImage(user, null, key("b"));
        userService.updateNameAndProfileImage(user, null, key("a"));

        runner.retryPendingTasks();

        assertThat(currentImage()).isEqualTo(key("a"));
        assertThat(taskRepository.count()).isZero();
        verify(s3Client, never()).deleteObject(DeleteObjectRequest.builder()
                .bucket("test-bucket").key(key("a")).build());
        verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(key("b")).build());
        verify(s3Client, never()).putObjectTagging(argThat(
                (PutObjectTaggingRequest request) -> request.key().equals(key("b"))));
    }

    @Test
    @DisplayName("기본 이미지와 다른 사용자의 이미지는 후처리하지 않아야 한다")
    void process_WithUnsafeTask_DiscardsTaskWithoutS3Changes() {
        List<Long> ids = new TransactionTemplate(transactionManager).execute(status -> List.of(
                taskRepository.save(new ProfileImageTask(user.getId(), ProfileImageTask.Type.DELETE,
                        "defaults/profile.png")).getId(),
                taskRepository.save(new ProfileImageTask(user.getId(), ProfileImageTask.Type.DELETE,
                        "profiles/999999/other-user")).getId()));

        ids.forEach(processor::process);

        assertThat(taskRepository.count()).isZero();
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("기본 이미지로 돌아가면 이전 이미지만 삭제해야 한다")
    void updateProfile_ToDefault_DeletesOnlyPreviousImage() {
        setCurrentImage(key("old"));

        userService.updateNameAndProfileImage(user, null, "defaults/profile.png");
        runner.retryPendingTasks();

        assertThat(currentImage()).isEqualTo("defaults/profile.png");
        assertThat(taskRepository.count()).isZero();
        verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(key("old")).build());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
    }

    @Test
    @DisplayName("같은 작업을 동시에 실행해도 S3 삭제는 한 번만 수행해야 한다")
    void process_Concurrently_DeletesObjectOnce() throws Exception {
        Long taskId = taskRepository.saveAndFlush(new ProfileImageTask(
                user.getId(), ProfileImageTask.Type.DELETE, key("old"))).getId();
        CountDownLatch deletionStarted = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
            deletionStarted.countDown();
            if (!releaseDeletion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 삭제 대기 시간 초과");
            }
            return DeleteObjectResponse.builder().build();
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processor.process(taskId));
            try {
                assertThat(deletionStarted.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    processor.process(taskId);
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                releaseDeletion.countDown();
                first.get(15, TimeUnit.SECONDS);
                second.get(15, TimeUnit.SECONDS);
            } finally {
                releaseDeletion.countDown();
            }
        }

        processor.process(taskId);
        assertThat(taskRepository.count()).isZero();
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("삭제 중인 이미지를 다시 선택하면 삭제가 끝난 뒤 업로드 검증을 해야 한다")
    void updateProfile_WhileDeletingObject_RejectsDeletedImage() throws Exception {
        Long taskId = taskRepository.saveAndFlush(new ProfileImageTask(
                user.getId(), ProfileImageTask.Type.DELETE, key("old"))).getId();
        CountDownLatch deletionStarted = new CountDownLatch(1);
        CountDownLatch releaseDeletion = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        AtomicBoolean deleted = new AtomicBoolean();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
            deletionStarted.countDown();
            if (!releaseDeletion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 삭제 대기 시간 초과");
            }
            deleted.set(true);
            return DeleteObjectResponse.builder().build();
        });
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            if (deleted.get()) {
                throw S3Exception.builder().statusCode(404).build();
            }
            return HeadObjectResponse.builder().build();
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> processor.process(taskId));
            try {
                assertThat(deletionStarted.await(10, TimeUnit.SECONDS)).isTrue();
                var update = executor.submit(() -> {
                    updateStarted.countDown();
                    userService.updateNameAndProfileImage(user, null, key("old"));
                });
                assertThat(updateStarted.await(10, TimeUnit.SECONDS)).isTrue();
                releaseDeletion.countDown();
                deletion.get(15, TimeUnit.SECONDS);
                assertThatThrownBy(() -> update.get(15, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(ProfileImageNotUploadedException.class);
            } finally {
                releaseDeletion.countDown();
            }
        }

        assertThat(currentImage()).isEqualTo("defaults/profile.png");
        assertThat(taskRepository.count()).isZero();
    }

    private String key(String name) {
        return "profiles/" + user.getId() + "/" + name;
    }

    private void setCurrentImage(String key) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.find(User.class, user.getId()).updateNameAndProfileImage(user.getNickname(), key));
    }

    private String currentImage() {
        return jdbc.queryForObject("select profile_image from users where user_id = ?", String.class, user.getId());
    }

    private void makeTasksDue() {
        jdbc.update("update profile_image_tasks set next_attempt_at = ?", LocalDateTime.now().minusMinutes(1));
    }

    private RuntimeException storageFailure() {
        return S3Exception.builder().statusCode(503).build();
    }
}
