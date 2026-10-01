package com.ufo.ufo.global.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.application.ChatSubscriptionAccessService;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.config.CorsProperties;
import com.ufo.ufo.global.config.WebSocketConfig;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@SpringBootTest(
        classes = StompSecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jwt.secret=" + StompSecurityIntegrationTest.JWT_SECRET,
                "spring.jwt.access-token-expire=60000",
                "spring.jwt.refresh-token-expire=120000"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("STOMP 접근 제어 통합 테스트")
class StompSecurityIntegrationTest {

    static final String JWT_SECRET = "c3RvbXAtc2VjdXJpdHktdGVzdC1rZXktMzItYnl0ZXM=";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SubscriptionProbe subscriptionProbe;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ChatRoomRepository chatRoomRepository;

    @MockitoBean
    private ChatRoomStatusRepository chatRoomStatusRepository;

    private final List<StompConnection> connections = new ArrayList<>();
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        User member = UserFixture.createUserWithId(1L);
        User admin = UserFixture.createUser("admin@example.com", Role.ROLE_ADMIN);
        UserFixture.setId(admin, 2L);
        User deleted = UserFixture.createUser("deleted@example.com", Role.ROLE_USER);
        UserFixture.setId(deleted, 3L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 4L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(PatternFixture.createPatternWithId(100L), 10L);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(member));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(deleted));
        when(userRepository.findByEmail("guest@example.com")).thenReturn(Optional.of(guest));
        when(chatRoomRepository.existsByIdAndPattern_DeletedAtIsNull(10L)).thenReturn(true);
        when(chatRoomRepository.existsByIdAndPattern_DeletedAtIsNull(20L)).thenReturn(true);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, 10L))
                .thenReturn(Optional.of(ChatRoomStatus.builder().user(member).room(room).nickname("민트 메리노").build()));
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(3L, 10L))
                .thenReturn(Optional.of(ChatRoomStatus.builder().user(deleted).room(room).nickname("블루 코튼").build()));
    }

    @AfterEach
    void tearDown() {
        connections.forEach(connection -> connection.socket.abort());
        httpClient.shutdownNow();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not-a-token"})
    @DisplayName("토큰이 없거나 유효하지 않으면 ERROR 응답 후 연결을 종료해야 한다")
    void rejectsMissingOrInvalidTokenDuringConnect(String token) throws Exception {
        StompConnection connection = open(token);

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
        connection.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("만료된 Access Token으로 연결하면 ERROR 응답 후 연결을 종료해야 한다")
    void rejectsExpiredAccessTokenDuringConnect() throws Exception {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(JWT_SECRET);
        ReflectionTestUtils.setField(expiredTokenProvider, "accessTokenExpireTime", -1000L);
        StompConnection connection = open(expiredTokenProvider.createAccessToken("test@example.com", Role.ROLE_USER.name()));

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
        connection.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("서명된 Refresh Token으로 STOMP에 연결할 수 없어야 한다")
    void rejectsRefreshTokenDuringConnect() throws Exception {
        StompConnection connection = open(tokenProvider.createRefreshToken("test@example.com"));

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
        connection.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("ROLE_GUEST Access Token으로 정상 연결할 수 있어야 한다")
    void allowsGuestAccessTokenDuringConnect() throws Exception {
        StompConnection connection = connect("guest@example.com", Role.ROLE_GUEST);

        assertThat(connection.socket.isOutputClosed()).isFalse();
    }

    @Test
    @DisplayName("참여자는 메시지·읽음 전송 경로를 사용하고 실시간 응답을 받아야 한다")
    void memberReceivesBroadcastAndCanUseBothApplicationRoutes() throws Exception {
        StompConnection connection = connect("test@example.com", Role.ROLE_USER);
        subscribeAndWait(connection, "/sub/chat/rooms/10");

        connection.send("SEND\ndestination:/pub/chat/message\ncontent-type:text/plain\n\nhello");
        assertThat(connection.nextFrame()).contains("MESSAGE\n").endsWith("\n\ntest@example.com:hello");
        connection.send("SEND\ndestination:/pub/chat/read\ncontent-type:text/plain\n\nread");
        assertThat(connection.nextFrame()).endsWith("\n\ntest@example.com:read");
    }

    @Test
    @DisplayName("일반 사용자가 참여하지 않은 채팅방을 구독하면 거부해야 한다")
    void rejectsSubscriptionToAnotherRoom() throws Exception {
        StompConnection connection = connect("test@example.com", Role.ROLE_USER);
        connection.subscribe("/sub/chat/rooms/20", UUID.randomUUID().toString());

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    @Test
    @DisplayName("토큰과 DB 권한이 모두 관리자이면 미참여 활성 채팅방도 구독할 수 있어야 한다")
    void administratorCanReadAnActiveRoomWithoutMembership() throws Exception {
        StompConnection connection = connect("admin@example.com", Role.ROLE_ADMIN);
        subscribeAndWait(connection, "/sub/chat/rooms/20");

        messagingTemplate.convertAndSend("/sub/chat/rooms/20", "admin broadcast");

        assertThat(connection.nextFrame()).endsWith("\n\nadmin broadcast");
    }

    @Test
    @DisplayName("토큰이 관리자여도 DB 계정이 일반 사용자이면 다른 방 구독을 거부해야 한다")
    void tokenAdminClaimDoesNotOverrideCurrentDatabaseRole() throws Exception {
        StompConnection connection = connect("test@example.com", Role.ROLE_ADMIN);
        connection.subscribe("/sub/chat/rooms/20", UUID.randomUUID().toString());

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    @Test
    @DisplayName("DB 계정이 관리자여도 토큰에 관리자 권한이 없으면 미참여 방 구독을 거부해야 한다")
    void administratorWithoutAdminAuthorityCannotBypassMembership() throws Exception {
        StompConnection connection = connect("admin@example.com", Role.ROLE_USER);
        connection.subscribe("/sub/chat/rooms/20", UUID.randomUUID().toString());

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing@example.com", "deleted@example.com"})
    @DisplayName("사용자가 없거나 삭제된 경우 채팅방 구독을 거부해야 한다")
    void rejectsSubscriptionsForMissingOrDeletedUsers(String email) throws Exception {
        StompConnection connection = connect(email, Role.ROLE_USER);
        connection.subscribe("/sub/chat/rooms/10", UUID.randomUUID().toString());

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    @Test
    @DisplayName("관리자도 활성 채팅방이 아니면 구독할 수 없어야 한다")
    void rejectsSubscriptionToUnavailableRoom() throws Exception {
        StompConnection connection = connect("admin@example.com", Role.ROLE_ADMIN);
        connection.subscribe("/sub/chat/rooms/30", UUID.randomUUID().toString());

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/sub/chat/rooms/10", "/pub/chat/unknown", "/pub/chat/message/extra"})
    @DisplayName("브로커 직접 전송과 허용되지 않은 pub 경로 전송을 거부해야 한다")
    void rejectsDirectBrokerSendAndUnsupportedApplicationRoutes(String destination) throws Exception {
        StompConnection connection = connect("test@example.com", Role.ROLE_USER);
        subscribeAndWait(connection, "/sub/chat/rooms/10");
        connection.send("SEND\ndestination:" + destination + "\ncontent-type:text/plain\n\nforged");

        assertThat(connection.nextFrame()).startsWith("ERROR\n");
    }

    private StompConnection connect(String email, Role role) throws Exception {
        StompConnection connection = open(tokenProvider.createAccessToken(email, role.name()));
        assertThat(connection.nextFrame()).startsWith("CONNECTED\n");
        return connection;
    }

    private StompConnection open(String token) throws Exception {
        StompConnection connection = new StompConnection();
        connection.socket = httpClient.newWebSocketBuilder()
                .header("Origin", "http://localhost")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), connection)
                .get(5, TimeUnit.SECONDS);
        connections.add(connection);
        String authorization = token == null ? "" : "Authorization:Bearer " + token + "\n";
        connection.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\n" + authorization + "\n");
        return connection;
    }

    private void subscribeAndWait(StompConnection connection, String destination) throws Exception {
        String subscriptionId = UUID.randomUUID().toString();
        CompletableFuture<Void> registered = subscriptionProbe.expect(subscriptionId);
        connection.subscribe(destination, subscriptionId);
        registered.get(5, TimeUnit.SECONDS);
    }

    private static class StompConnection implements WebSocket.Listener {

        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<Void> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            int end;
            while ((end = text.indexOf("\0")) >= 0) {
                frames.add(text.substring(0, end));
                text.delete(0, end + 1);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        void send(String frame) throws Exception {
            socket.sendText(frame + "\0", true).get(5, TimeUnit.SECONDS);
        }

        void subscribe(String destination, String subscriptionId) throws Exception {
            send("SUBSCRIBE\nid:" + subscriptionId + "\ndestination:" + destination + "\n\n");
        }

        String nextFrame() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("서버의 STOMP 응답 프레임").isNotNull();
            return frame;
        }
    }

    private static class SubscriptionProbe implements ExecutorChannelInterceptor {

        private final Map<String, CompletableFuture<Void>> expected = new ConcurrentHashMap<>();

        CompletableFuture<Void> expect(String subscriptionId) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            expected.put(subscriptionId, future);
            return future;
        }

        @Override
        public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                        Exception exception) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (exception == null && handler instanceof SimpleBrokerMessageHandler
                    && accessor.getCommand() == StompCommand.SUBSCRIBE) {
                CompletableFuture<Void> future = expected.remove(accessor.getSubscriptionId());
                if (future != null) {
                    future.complete(null);
                }
            }
        }
    }

    @Controller
    static class EchoController {

        private final SimpMessagingTemplate messagingTemplate;

        EchoController(SimpMessagingTemplate messagingTemplate) {
            this.messagingTemplate = messagingTemplate;
        }

        @MessageMapping({"/chat/message", "/chat/read"})
        void echo(Principal principal, String payload) {
            messagingTemplate.convertAndSend("/sub/chat/rooms/10", principal.getName() + ":" + payload);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            OAuth2ClientAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, StompJwtAuthChannelInterceptor.class, JwtTokenProvider.class,
            ChatSubscriptionAccessService.class, EchoController.class})
    static class TestApplication implements WebSocketMessageBrokerConfigurer {

        private final SubscriptionProbe subscriptionProbe = new SubscriptionProbe();

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of("http://localhost"));
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }

        @Bean
        SubscriptionProbe subscriptionProbe() {
            return subscriptionProbe;
        }

        @Override
        public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(subscriptionProbe);
        }
    }
}
