package com.ufo.ufo.domain.user.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 사용자 잠금 Repository 통합 테스트")
class UserLockRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("잠금 조회는 이미 읽은 사용자도 최신 DB 상태로 갱신한다")
    void refreshesStaleManagedUserWhenLocking() {
        User saved = createUser();
        transaction().executeWithoutResult(status -> {
            User stale = userRepository.findById(saved.getId()).orElseThrow();
            assertThat(stale.getBallBalance()).isEqualTo(10);

            TransactionTemplate separate = transaction();
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            separate.executeWithoutResult(inner ->
                    userRepository.findById(saved.getId()).orElseThrow().addCredits(140));

            assertThat(stale.getBallBalance()).isEqualTo(10);
            User locked = userRepository.findByIdForUpdate(saved.getId()).orElseThrow();
            assertThat(locked).isSameAs(stale);
            assertThat(locked.getBallBalance()).isEqualTo(150);
        });
    }

    @Test
    @DisplayName("같은 트랜잭션에서 다시 잠가도 미저장 잔액과 프로필 변경을 유지한다")
    void reentrantLockPreservesPendingChanges() {
        User saved = createUser();
        transaction().executeWithoutResult(status -> {
            User locked = userRepository.findByIdForUpdate(saved.getId()).orElseThrow();
            locked.addCredits(140);
            locked.updateNameAndProfileImage("updated" + saved.getId(), "new.png");

            User reentrant = userRepository.findByIdForUpdate(saved.getId()).orElseThrow();
            assertThat(reentrant.getBallBalance()).isEqualTo(150);
            assertThat(reentrant.getNickname()).isEqualTo("updated" + saved.getId());
            assertThat(reentrant.getProfileImage()).isEqualTo("new.png");
        });
        User committed = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(committed.getBallBalance()).isEqualTo(150);
        assertThat(committed.getNickname()).isEqualTo("updated" + saved.getId());
        assertThat(committed.getProfileImage()).isEqualTo("new.png");
    }

    @Test
    @DisplayName("테스트 설정에서도 생성·수정 시각을 기록한다")
    void persistsAuditDates() {
        User saved = createUser();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private User createUser() {
        User user = UserFixture.createUser(UUID.randomUUID() + "@example.com",
                Role.ROLE_USER);
        user.updateNameAndProfileImage(UUID.randomUUID().toString().substring(0, 16), "defaults/profile.png");
        user.addCredits(10);
        return userRepository.saveAndFlush(user);
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }
}
