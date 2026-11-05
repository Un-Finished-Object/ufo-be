package com.ufo.ufo.global.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.auth.application.AuthSessionService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.global.security.jwt.AccessTokenContext;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

@DataJpaTest(properties = {
        "spring.config.name=application-db-test",
        "spring.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jwt.access-token-expire=120000", "spring.jwt.refresh-token-expire=60000",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=500"
}, showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, JwtTokenProvider.class,
        AuthSessionService.class, AuthenticatedWebSocketSessions.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("STOMP 송신 인증과 DB 연결 사용 통합 테스트")
class StompSessionOutboundIntegrationTest {

    @Autowired
    private AuthSessionService auth;
    @Autowired
    private AuthenticatedWebSocketSessions sockets;
    @Autowired
    private UserRepository users;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("업무 트랜잭션이 유일한 DB 연결을 사용해도 정상 메시지를 전달해야 한다")
    void sendsWithoutBorrowingAnotherConnectionOnPublishingThread() throws Exception {
        Login login = login();
        BlockingQueue<Message<?>> received = new LinkedBlockingQueue<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var channel = new ExecutorSubscribableChannel(executor);
            channel.addInterceptor(new StompSessionOutboundInterceptor(sockets));
            channel.subscribe(received::add);

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(users.findByEmail(login.context().email())).isPresent();
                assertThat(channel.send(message(login))).isTrue();
            });

            assertThat(received.poll(5, TimeUnit.SECONDS)).isNotNull()
                    .extracting(Message::getPayload).isEqualTo("private-message");
            verify(login.socket(), never()).close(any());
        } finally {
            sockets.closed(login.socket().getId());
        }
    }

    @Test
    @DisplayName("전송 대기 중 로그아웃하면 대기 중인 메시지도 전달하지 않아야 한다")
    void checksRevocationImmediatelyBeforeDelivery() throws Exception {
        Login login = login();
        BlockingQueue<Runnable> pending = new LinkedBlockingQueue<>();
        BlockingQueue<Message<?>> received = new LinkedBlockingQueue<>();
        var channel = new ExecutorSubscribableChannel(pending::add);
        channel.addInterceptor(new StompSessionOutboundInterceptor(sockets));
        channel.subscribe(received::add);
        assertThat(channel.send(message(login))).isTrue();
        Runnable delivery = pending.poll(5, TimeUnit.SECONDS);
        assertThat(delivery).isNotNull();
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + login.access());
        auth.logout(request);

        delivery.run();

        assertThat(received).isEmpty();
        verify(login.socket()).close(any());
    }

    private Login login() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String email = id + "@example.test";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = UserFixture.createUser(email, Role.ROLE_USER);
            user.updateNameAndProfileImage(id.substring(0, 16), "defaults/profile.png");
            users.saveAndFlush(user);
        });
        String refresh = auth.issueRefreshToken(email);
        String access = auth.reissue(refresh).accessToken();
        var context = (AccessTokenContext) auth.authenticateAccessToken(access).getDetails();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        sockets.opened(socket);
        sockets.authenticate(id, context);
        return new Login(access, context, socket);
    }

    private Message<String> message(Login login) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(login.socket().getId());
        headers.setDestination("/sub/chat/rooms/10");
        return MessageBuilder.createMessage("private-message", headers.getMessageHeaders());
    }

    private record Login(String access, AccessTokenContext context, WebSocketSession socket) {
    }
}
