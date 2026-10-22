package com.ufo.ufo.domain.pattern.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.dao.PatternViewLogRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.domain.PatternViewLog;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dao.ScrapRepository;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseLockObserver;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Timeout(45)
@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class,
        PatternCounterConcurrencyIntegrationTest.TestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 도안 조회수와 찜 수 동시성 통합 테스트")
class PatternCounterConcurrencyIntegrationTest {

    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private PatternViewLogRepository viewLogRepository;
    @Autowired
    private ScrapRepository scrapRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatternViewService viewService;
    @Autowired
    private ScrapService scrapService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DatabaseLockObserver lockObserver;

    @Test
    @DisplayName("다른 사용자가 동시에 조회하면 두 조회 기록과 증가분을 모두 저장한다")
    void concurrentViewsPreserveBothIncrements() throws Exception {
        Fixture fixture = fixture();

        concurrently(false,
                () -> viewService.increaseViewCount(fixture.first, fixture.id()),
                () -> viewService.increaseViewCount(fixture.second, fixture.id()));

        assertState(fixture, 2, 0);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 조회해도 하루 조회수와 기록을 한 번만 저장한다")
    void concurrentDuplicateViewsCountOnce() throws Exception {
        Fixture fixture = fixture();

        concurrently(false,
                () -> viewService.increaseViewCount(fixture.first, fixture.id()),
                () -> viewService.increaseViewCount(fixture.first, fixture.id()));

        assertState(fixture, 1, 0);
    }

    @Test
    @DisplayName("어제 조회한 사용자도 오늘 조회수와 기록을 한 번 추가할 수 있다")
    void previousDayViewDoesNotBlockTodaysView() {
        Fixture fixture = fixture();
        transaction().executeWithoutResult(status -> {
            Pattern pattern = patternRepository.findById(fixture.id()).orElseThrow();
            pattern.increaseViewCount();
            viewLogRepository.save(PatternViewLog.builder().pattern(pattern).user(fixture.first)
                    .viewedDate(LocalDate.now().minusDays(1)).build());
        });

        viewService.increaseViewCount(fixture.first, fixture.id());
        viewService.increaseViewCount(fixture.first, fixture.id());

        assertState(fixture, 2, 0);
    }

    @Test
    @DisplayName("다른 사용자가 동시에 찜하면 두 찜 기록과 증가분을 모두 저장한다")
    void concurrentScrapsPreserveBothIncrements() throws Exception {
        Fixture fixture = fixture();

        concurrently(false,
                () -> scrapService.addPatternScrap(fixture.first, fixture.id()),
                () -> scrapService.addPatternScrap(fixture.second, fixture.id()));

        assertState(fixture, 0, 2);
    }

    @Test
    @DisplayName("같은 사용자의 동시 찜 요청은 한 번만 저장하고 정상 응답한다")
    void concurrentDuplicateScrapsCountOnce() throws Exception {
        Fixture fixture = fixture();

        concurrently(false,
                () -> scrapService.addPatternScrap(fixture.first, fixture.id()),
                () -> scrapService.addPatternScrap(fixture.first, fixture.id()));

        assertState(fixture, 0, 1);
    }

    @Test
    @DisplayName("다른 사용자가 동시에 찜을 취소하면 두 감소분을 모두 반영한다")
    void concurrentRemovalsPreserveBothDecrements() throws Exception {
        Fixture fixture = fixture();
        scrapService.addPatternScrap(fixture.first, fixture.id());
        scrapService.addPatternScrap(fixture.second, fixture.id());

        concurrently(false,
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()),
                () -> scrapService.removePatternScrap(fixture.second, fixture.id()));

        assertState(fixture, 0, 0);
    }

    @Test
    @DisplayName("같은 찜을 동시에 취소해도 다른 사용자의 찜 수는 유지한다")
    void concurrentDuplicateRemovalPreservesOtherUsersScrap() throws Exception {
        Fixture fixture = fixture();
        scrapService.addPatternScrap(fixture.first, fixture.id());
        scrapService.addPatternScrap(fixture.second, fixture.id());

        concurrently(false,
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()),
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()));

        assertState(fixture, 0, 1);
        assertThat(scrapRepository.existsByUser_IdAndPattern_Id(fixture.second.getId(), fixture.id())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("조회와 찜 추가가 동시에 실행되어도 서로의 카운터를 덮어쓰지 않는다")
    void concurrentViewAndScrapPreserveBothCounters(boolean reverseOrder) throws Exception {
        Fixture fixture = fixture();

        concurrently(reverseOrder,
                () -> viewService.increaseViewCount(fixture.first, fixture.id()),
                () -> scrapService.addPatternScrap(fixture.second, fixture.id()));

        assertState(fixture, 1, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("조회와 찜 취소가 동시에 실행되어도 서로의 카운터를 덮어쓰지 않는다")
    void concurrentViewAndRemovalPreserveBothCounters(boolean reverseOrder) throws Exception {
        Fixture fixture = fixture();
        scrapService.addPatternScrap(fixture.first, fixture.id());

        concurrently(reverseOrder,
                () -> viewService.increaseViewCount(fixture.second, fixture.id()),
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()));

        assertState(fixture, 1, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("한 사용자의 찜 취소와 다른 사용자의 찜 추가를 모두 반영한다")
    void concurrentAdditionAndRemovalPreserveRecords(boolean reverseOrder) throws Exception {
        Fixture fixture = fixture();
        scrapService.addPatternScrap(fixture.first, fixture.id());

        concurrently(reverseOrder,
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()),
                () -> scrapService.addPatternScrap(fixture.second, fixture.id()));

        assertState(fixture, 0, 1);
        assertThat(scrapRepository.existsByUser_IdAndPattern_Id(fixture.first.getId(), fixture.id())).isFalse();
        assertThat(scrapRepository.existsByUser_IdAndPattern_Id(fixture.second.getId(), fixture.id())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("같은 사용자의 찜 추가와 취소가 겹쳐도 최종 카운터와 기록이 일치한다")
    void concurrentToggleKeepsCounterConsistent(boolean reverseOrder) throws Exception {
        Fixture fixture = fixture();

        concurrently(reverseOrder,
                () -> scrapService.addPatternScrap(fixture.first, fixture.id()),
                () -> scrapService.removePatternScrap(fixture.first, fixture.id()));

        assertState(fixture, 0, reverseOrder ? 1 : 0);
    }

    @ParameterizedTest
    @EnumSource(CounterOperation.class)
    @DisplayName("사용자와 도안을 잠그는 구매 처리에 겹쳐도 카운터 요청이 교착되지 않는다")
    void counterUpdatesFollowUserThenPatternLockOrder(CounterOperation operation) throws Exception {
        Fixture fixture = fixture();
        CompletableFuture<Long> waitingSession = new CompletableFuture<>();
        AtomicReference<Future<?>> pending = new AtomicReference<>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            transaction().executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(fixture.first.getId()).orElseThrow();
                long blockingSession = lockObserver.currentSessionId();
                pending.set(submitRequest(executor, waitingSession, () -> {
                    if (operation == CounterOperation.VIEW) {
                        viewService.increaseViewCount(fixture.first, fixture.id());
                    } else {
                        scrapService.addPatternScrap(fixture.first, fixture.id());
                    }
                }));
                lockObserver.awaitBlockedBy(sessionId(waitingSession), blockingSession, pending.get());

                // 구매는 사용자 락을 보유한 상태에서 도안 락을 요청한다.
                patternRepository.findByIdAndDeletedAtIsNull(fixture.id()).orElseThrow();
            });
            pending.get().get(30, TimeUnit.SECONDS);
        } finally {
            if (pending.get() != null) {
                pending.get().cancel(true);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertState(fixture, operation == CounterOperation.VIEW ? 1 : 0,
                operation == CounterOperation.SCRAP ? 1 : 0);
    }

    @Test
    @DisplayName("이미 읽은 도안으로 조회수를 갱신해도 다른 트랜잭션의 증가분을 유지한다")
    void staleManagedPatternDoesNotLoseViews() {
        Fixture fixture = fixture();

        transaction().executeWithoutResult(status -> {
            Pattern stale = patternRepository.findById(fixture.id()).orElseThrow();
            separateTransaction().executeWithoutResult(inner ->
                    viewService.increaseViewCount(fixture.first, fixture.id()));
            assertThat(stale.getViewCount()).isZero();

            viewService.increaseViewCount(fixture.second, fixture.id());
        });

        assertState(fixture, 2, 0);
    }

    @Test
    @DisplayName("이미 읽은 도안의 찜 수를 갱신해도 다른 트랜잭션의 조회수를 덮어쓰지 않는다")
    void staleManagedPatternDoesNotOverwriteOtherCounter() {
        Fixture fixture = fixture();

        transaction().executeWithoutResult(status -> {
            patternRepository.findById(fixture.id()).orElseThrow();
            separateTransaction().executeWithoutResult(inner ->
                    viewService.increaseViewCount(fixture.first, fixture.id()));

            scrapService.addPatternScrap(fixture.second, fixture.id());
        });

        assertState(fixture, 1, 1);
    }

    @Test
    @DisplayName("같은 트랜잭션에서 조회와 찜을 반복해도 미저장 변경을 잃지 않는다")
    void repeatedUpdatesInOneTransactionPreserveBothCounters() {
        Fixture fixture = fixture();

        transaction().executeWithoutResult(status -> {
            viewService.increaseViewCount(fixture.first, fixture.id());
            scrapService.addPatternScrap(fixture.first, fixture.id());
            viewService.increaseViewCount(fixture.first, fixture.id());
        });

        assertState(fixture, 1, 1);
    }

    @Test
    @DisplayName("같은 도안 락을 다시 획득해도 아직 저장하지 않은 카운터 변경을 버리지 않는다")
    void repeatedLockPreservesPendingCounterChanges() {
        Fixture fixture = fixture();

        transaction().executeWithoutResult(status -> {
            Pattern locked = patternRepository.findByIdForUpdate(fixture.id()).orElseThrow();
            locked.increaseViewCount();
            locked.increaseScrapsCount();

            Pattern reentrant = patternRepository.findByIdForUpdate(fixture.id()).orElseThrow();

            assertThat(reentrant).isSameAs(locked);
            assertThat(reentrant.getViewCount()).isEqualTo(1);
            assertThat(reentrant.getScrapsCount()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertState(fixture, 0, 0);
    }

    @Test
    @DisplayName("조회수 증가와 찜 추가 후 실패하면 카운터와 두 기록을 모두 롤백한다")
    void rollbackDiscardsCountersAndNewRecords() {
        Fixture fixture = fixture();
        var failure = new IllegalStateException("카운터 변경 후 실패");

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            viewService.increaseViewCount(fixture.first, fixture.id());
            scrapService.addPatternScrap(fixture.first, fixture.id());
            throw failure;
        })).isSameAs(failure);

        assertState(fixture, 0, 0);
    }

    @Test
    @DisplayName("찜 취소 후 실패하면 카운터와 기존 찜 기록을 복구한다")
    void rollbackRestoresRemovedScrap() {
        Fixture fixture = fixture();
        scrapService.addPatternScrap(fixture.first, fixture.id());
        var failure = new IllegalStateException("찜 취소 후 실패");

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            scrapService.removePatternScrap(fixture.first, fixture.id());
            throw failure;
        })).isSameAs(failure);

        assertState(fixture, 0, 1);
    }

    private Fixture fixture() {
        Pattern pattern = patternRepository.saveAndFlush(PatternFixture.createPattern());
        return new Fixture(pattern.getId(), createUser(), createUser());
    }

    private User createUser() {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        User user = UserFixture.createUser(identifier + "@example.com", Role.ROLE_USER);
        user.updateNameAndProfileImage(identifier.substring(0, 16), "defaults/profile.png");
        return userRepository.saveAndFlush(user);
    }

    private void assertState(Fixture fixture, int views, int scraps) {
        Pattern pattern = patternRepository.findById(fixture.id()).orElseThrow();
        assertThat(pattern.getViewCount()).isEqualTo(views);
        assertThat(pattern.getScrapsCount()).isEqualTo(scraps);
        assertThat(viewLogRepository.findAll().stream()
                .filter(log -> log.getPattern().getId().equals(fixture.id())).count()).isEqualTo(views);
        assertThat(scrapRepository.findAll().stream()
                .filter(scrap -> scrap.getPattern().getId().equals(fixture.id())).count()).isEqualTo(scraps);
    }

    private void concurrently(boolean reverseOrder, Runnable first, Runnable second) throws Exception {
        Runnable holder = reverseOrder ? second : first;
        Runnable waiter = reverseOrder ? first : second;
        CompletableFuture<Long> waitingSession = new CompletableFuture<>();
        AtomicReference<Future<?>> pending = new AtomicReference<>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            transaction().executeWithoutResult(status -> {
                long blockingSession = lockObserver.currentSessionId();
                holder.run();
                patternRepository.flush();
                pending.set(submitRequest(executor, waitingSession, waiter));

                // 첫 요청을 커밋하기 전에 두 번째 요청의 실제 DB 락 대기를 확인한다.
                lockObserver.awaitBlockedBy(sessionId(waitingSession), blockingSession, pending.get());
            });
            pending.get().get(30, TimeUnit.SECONDS);
        } finally {
            if (pending.get() != null) {
                pending.get().cancel(true);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Future<?> submitRequest(ExecutorService executor, CompletableFuture<Long> session, Runnable request) {
        return executor.submit(() -> {
            try {
                transaction().executeWithoutResult(status -> {
                    session.complete(lockObserver.currentSessionId());
                    request.run();
                });
            } catch (RuntimeException | Error failure) {
                session.completeExceptionally(failure);
                throw failure;
            }
        });
    }

    private long sessionId(CompletableFuture<Long> session) {
        try {
            return session.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("테스트 요청의 DB 세션을 확인할 수 없습니다.", exception);
        }
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private TransactionTemplate separateTransaction() {
        TransactionTemplate transaction = transaction();
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private record Fixture(Long id, User first, User second) {
    }

    private enum CounterOperation {
        VIEW, SCRAP
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({PatternViewService.class, ScrapService.class, UserService.class})
    static class TestConfig {

        @Bean
        DatabaseLockObserver databaseLockObserver(DataSource dataSource, ObjectProvider<MySQLContainer> mysql) {
            return new DatabaseLockObserver(dataSource, mysql.getIfAvailable());
        }

        @Bean
        ImageService imageService() {
            return mock(ImageService.class);
        }
    }
}
