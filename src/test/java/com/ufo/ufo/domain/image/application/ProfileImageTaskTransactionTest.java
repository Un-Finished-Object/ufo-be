package com.ufo.ufo.domain.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.image.dao.ProfileImageTaskRepository;
import com.ufo.ufo.domain.image.domain.ProfileImageTask;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.fixture.ProfileImageTestConfig;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({JpaAuditingTestConfig.class, ProfileImageTestConfig.class, UserService.class, ImageService.class,
        ImageKeyPolicy.class, ImageUploadValidator.class, S3ImageObjectManager.class,
        ProfileImageChangedEventListener.class, ProfileImageTaskRunner.class, ProfileImageTaskProcessor.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("프로필 이미지 후처리 작업 트랜잭션 테스트")
class ProfileImageTaskTransactionTest {

    @Autowired
    private UserService userService;
    @Autowired
    private ProfileImageTaskRunner runner;
    @MockitoSpyBean
    private ProfileImageTaskRepository taskRepository;
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
    @DisplayName("S3 태그 변경에 실패해도 프로필과 재처리할 작업은 DB에 남아야 한다")
    void updateProfile_WithTagFailure_PreservesProfileAndPendingTask() {
        when(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());
        String key = "profiles/" + user.getId() + "/new-avatar";

        userService.updateNameAndProfileImage(user, null, key);
        runner.retryPendingTasks();

        assertThat(jdbc.queryForObject("select profile_image from users where user_id = ?",
                String.class, user.getId())).isEqualTo(key);
        assertThat(pendingTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("프로필 변경이 롤백되면 후처리 작업과 S3 변경이 남지 않아야 한다")
    void updateProfile_WhenRolledBack_DiscardsTaskAndS3Changes() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userService.updateNameAndProfileImage(user, null, "profiles/" + user.getId() + "/rolled-back");
            verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
            status.setRollbackOnly();
        });

        assertThat(pendingTasks()).isZero();
        assertThat(jdbc.queryForObject("select profile_image from users where user_id = ?",
                String.class, user.getId())).isEqualTo("defaults/profile.png");
        verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
    }

    @Test
    @DisplayName("후처리 작업을 저장하지 못하면 프로필 변경도 롤백해야 한다")
    void updateProfile_WithTaskSaveFailure_RollsBackProfile() {
        doThrow(new DataIntegrityViolationException("작업 저장 실패"))
                .when(taskRepository).save(any(ProfileImageTask.class));

        assertThatThrownBy(() -> userService.updateNameAndProfileImage(user, null,
                "profiles/" + user.getId() + "/new-avatar"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("select profile_image from users where user_id = ?",
                String.class, user.getId())).isEqualTo("defaults/profile.png");
        assertThat(pendingTasks()).isZero();
        verify(s3Client, never()).putObjectTagging(any(PutObjectTaggingRequest.class));
    }

    @Test
    @DisplayName("실패한 작업이 많아도 새 작업의 실행을 계속 막지 않아야 한다")
    void retryPendingTasks_WithRepeatedFailures_ProcessesOlderDueTaskFirst() {
        String prefix = "profiles/" + user.getId() + "/";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int index = 0; index < 50; index++) {
                ProfileImageTask failed = new ProfileImageTask(user.getId(), ProfileImageTask.Type.DELETE,
                        prefix + "failed-" + index);
                failed.defer(LocalDateTime.now().minusSeconds(31));
                taskRepository.save(failed);
            }
            ProfileImageTask waiting = taskRepository.save(new ProfileImageTask(
                    user.getId(), ProfileImageTask.Type.DELETE, prefix + "waiting"));
            entityManager.flush();
            jdbc.update("update profile_image_tasks set next_attempt_at = ? where task_id = ?",
                    LocalDateTime.now().minusMinutes(1), waiting.getId());
        });
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
            DeleteObjectRequest request = invocation.getArgument(0);
            if (request.key().contains("failed-")) {
                throw S3Exception.builder().statusCode(503).build();
            }
            return DeleteObjectResponse.builder().build();
        });

        runner.retryPendingTasks();

        verify(s3Client).deleteObject(DeleteObjectRequest.builder()
                .bucket("test-bucket").key(prefix + "waiting").build());
        assertThat(pendingTasks()).isEqualTo(50);
    }

    private int pendingTasks() {
        return jdbc.queryForObject(
                "select count(*) from profile_image_tasks where user_id = ?", Integer.class, user.getId());
    }
}
