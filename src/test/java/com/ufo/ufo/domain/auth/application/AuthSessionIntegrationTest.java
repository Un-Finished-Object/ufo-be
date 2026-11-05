package com.ufo.ufo.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ufo.ufo.domain.auth.dao.AuthSessionRepository;
import com.ufo.ufo.domain.auth.dao.RevokedAccessTokenRepository;
import com.ufo.ufo.domain.auth.domain.AuthSession;
import com.ufo.ufo.domain.auth.domain.RevokedAccessToken;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidAccessTokenException;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.security.jwt.AccessTokenContext;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.config.name=application-db-test",
        "spring.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jwt.access-token-expire=120000", "spring.jwt.refresh-token-expire=60000"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, JwtTokenProvider.class,
        AuthSessionService.class, AuthSessionCleanup.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("실제 DB 로그인 무효화와 동시성 테스트")
class AuthSessionIntegrationTest {

    @Autowired
    private AuthSessionService service;
    @Autowired
    private AuthSessionRepository sessions;
    @Autowired
    private RevokedAccessTokenRepository revoked;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtTokenProvider provider;
    @Autowired
    private AuthSessionCleanup cleanup;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate transaction;
    private User user;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        sessions.deleteAllInBatch();
        revoked.deleteAllInBatch();
        String id = UUID.randomUUID().toString().replace("-", "");
        user = transaction.execute(status -> {
            User created = UserFixture.createUser(id + "@example.test", Role.ROLE_USER);
            created.updateNameAndProfileImage(id.substring(0, 16), "defaults/profile.png");
            entityManager.persist(created);
            return created;
        });
    }

    @Test
    @DisplayName("다른 서비스 인스턴스도 DB에 저장한 무효화 상태를 확인해야 한다")
    void anotherServerRejectsRevokedSession() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        service.logout(request(access, refresh));
        var otherServer = new AuthSessionService(provider, users, sessions, revoked);

        assertThatThrownBy(() -> transaction.execute(status -> otherServer.reissue(refresh)))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> transaction.execute(status -> otherServer.authenticateAccessToken(access)))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThat(sessions.findAll()).singleElement()
                .satisfies(session -> assertThat(session.getRevokedAt()).isNotNull());
    }

    @Test
    @DisplayName("재발급과 로그아웃이 겹쳐도 로그아웃 뒤 사용할 수 있는 토큰은 없어야 한다")
    void concurrentRefreshAndLogoutCannotRestoreSession() throws Exception {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var reissue = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    return service.reissue(refresh).accessToken();
                } catch (InvalidTokenException expected) {
                    return null;
                }
            });
            var logout = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                service.logout(request(access, refresh));
                return null;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            logout.get(15, TimeUnit.SECONDS);
            String issued = reissue.get(15, TimeUnit.SECONDS);
            if (issued != null) {
                assertThatThrownBy(() -> service.authenticateAccessToken(issued))
                        .isInstanceOf(InvalidAccessTokenException.class);
            }
        } finally {
            start.countDown();
        }
        assertThatThrownBy(() -> service.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 읽어 둔 로그인 상태도 락 획득 후 커밋된 무효화를 반영해야 한다")
    void staleManagedSessionDoesNotAllowRefreshAfterLogout() throws Exception {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        String sessionId = provider.readAccessToken(access).orElseThrow().sessionId();
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> transaction.execute(status -> {
                AuthSession stale = sessions.findById(sessionId).orElseThrow();
                assertThat(stale.getRevokedAt()).isNull();
                try {
                    executor.submit(() -> service.logout(request(access, refresh))).get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return service.reissue(refresh);
            })).isInstanceOf(InvalidTokenException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("이전 DB 스냅샷을 가진 요청에서도 커밋된 로그아웃을 반영해야 한다")
    void authenticationDoesNotInheritSnapshotBeforeLogout(boolean legacy) throws Exception {
        String refresh = legacy ? provider.createRefreshToken(user.getEmail())
                : service.issueRefreshToken(user.getEmail());
        String access = legacy ? provider.createAccessToken(user.getEmail(), "ROLE_USER")
                : service.reissue(refresh).accessToken();
        AccessTokenContext context = (AccessTokenContext) service.authenticateAccessToken(access).getDetails();
        var repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        try (var executor = Executors.newSingleThreadExecutor()) {
            repeatableRead.executeWithoutResult(status -> {
                sessions.count();
                assertThat(revoked.existsById(context.tokenHash())).isFalse();
                try {
                    executor.submit(() -> service.logout(request(access, refresh))).get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                // 호출자의 이전 스냅샷과 인증에 사용하는 최신 상태를 구분한다.
                if (legacy) {
                    assertThat(revoked.existsById(context.tokenHash())).isFalse();
                } else {
                    assertThat(sessions.existsByIdAndUser_EmailAndRevokedAtIsNullAndRetainUntilAfter(
                            context.sessionId(), context.email(), Instant.now())).isTrue();
                }
                assertThat(service.isActive(context)).isFalse();
                assertThatThrownBy(() -> service.authenticateAccessToken(access))
                        .isInstanceOf(InvalidAccessTokenException.class);
            });
        }
    }

    @Test
    @DisplayName("로그아웃 트랜잭션이 롤백되면 로그인과 토큰은 유효해야 한다")
    void rollbackDoesNotRevokeSession() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();

        transaction.executeWithoutResult(status -> {
            service.logout(request(access, refresh));
            status.setRollbackOnly();
        });

        assertThat(service.authenticateAccessToken(access).isAuthenticated()).isTrue();
        assertThat(service.reissue(refresh).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("구형 토큰 로그아웃을 반복해도 기록은 하나이며 다른 로그인은 유효해야 한다")
    void duplicateLegacyLogoutDoesNotRestoreOrDuplicateRecords() {
        String legacyRefresh = provider.createRefreshToken(user.getEmail());
        String legacyAccess = provider.createAccessToken(user.getEmail(), "ROLE_USER");
        String other = service.issueRefreshToken(user.getEmail());

        service.logout(request(legacyAccess, legacyRefresh));
        service.logout(request(legacyAccess, legacyRefresh));

        assertThat(revoked.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(2);
        assertThatThrownBy(() -> service.reissue(legacyRefresh)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.authenticateAccessToken(legacyAccess))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThat(service.reissue(other).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("정리 작업은 모든 토큰의 만료가 지난 로그인과 구형 토큰 해시만 삭제해야 한다")
    void cleanupOnlyDeletesExpiredRecords() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        String activeSession = provider.readAccessToken(access).orElseThrow().sessionId();
        String expiredId = UUID.randomUUID().toString();
        transaction.executeWithoutResult(status -> {
            sessions.save(new AuthSession(expiredId, users.findById(user.getId()).orElseThrow(),
                    Instant.now().minusSeconds(60)));
            revoked.save(new RevokedAccessToken("a".repeat(64),
                    Instant.now().minusSeconds(60)));
        });
        service.logout(request(access, null));

        cleanup.deleteExpired();

        assertThat(sessions.findById(expiredId)).isEmpty();
        assertThat(sessions.findById(activeSession)).isPresent();
        assertThat(revoked.count()).isZero();
        assertThatThrownBy(() -> service.authenticateAccessToken(access))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    private MockHttpServletRequest request(String access, String refresh) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        if (refresh != null) {
            request.setCookies(new Cookie("refresh_token", refresh));
        }
        return request;
    }
}
