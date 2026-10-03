package com.ufo.ufo.domain.credit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.attendance.application.AttendanceService;
import com.ufo.ufo.domain.attendance.dao.AttendanceCheckRepository;
import com.ufo.ufo.domain.auth.application.AuthService;
import com.ufo.ufo.domain.auth.dto.request.SignupRequest;
import com.ufo.ufo.domain.chat.application.ChatNicknameGenerator;
import com.ufo.ufo.domain.chat.application.ChatRoomProvisioningService;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.dao.CreditTransactionRepository;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.exception.InsufficientCreditException;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.pattern.application.PatternPurchaseService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.domain.referral.ReferralCodeProperties;
import com.ufo.ufo.domain.referral.application.ReferralCodeGenerator;
import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.referral.dto.request.RegisterReferralCodeRequest;
import com.ufo.ufo.domain.referral.exception.ReferralCodeAlreadyRegisteredException;
import com.ufo.ufo.domain.referral.dao.ReferralRegistrationRepository;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.request.UpdateMyInfoRequest;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Provider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.database.DatabaseTestApplication;
import com.ufo.ufo.support.database.JpaAuditingTestConfig;
import com.ufo.ufo.support.database.TestDatabaseConfig;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Timeout(45)
@DataJpaTest(properties = "spring.config.name=application-db-test", showSql = false)
@ContextConfiguration(classes = DatabaseTestApplication.class)
@Import({TestDatabaseConfig.class, JpaAuditingTestConfig.class, CreditConcurrencyIntegrationTest.TestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"app.chat.segment-days=7", "spring.jwt.access-token-expire=60000",
        "spring.jwt.refresh-token-expire=120000"})
@DisplayName("실제 DB 크레딧 동시성 통합 테스트")
class CreditConcurrencyIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatternRepository patternRepository;
    @Autowired
    private CreditTransactionRepository transactionRepository;
    @Autowired
    private UnlockRepository unlockRepository;
    @Autowired
    private ChatRoomStatusRepository chatRoomStatusRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private AttendanceCheckRepository attendanceRepository;
    @Autowired
    private ReferralRegistrationRepository referralRegistrationRepository;
    @Autowired
    private PatternPurchaseService purchaseService;
    @Autowired
    private CreditService creditService;
    @Autowired
    private UserService userService;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private ReferralService referralService;
    @Autowired
    private AuthService authService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("잔액 10의 서로 다른 동시 구매는 하나만 승인하고 나머지는 잔액 부족으로 거절한다")
    void differentPurchases_WithInsufficientBalance_ApproveOnlyOne() throws Exception {
        User user = createUser(10, Role.ROLE_USER);
        Pattern first = patternRepository.save(PatternFixture.createPattern());
        Pattern second = patternRepository.save(PatternFixture.createPattern());

        List<Throwable> failures = concurrently(user,
                current -> purchase(current, first, "yarn"),
                current -> purchase(current, second, "yarn"));

        assertThat(failures).hasSize(1).allMatch(InsufficientCreditException.class::isInstance);
        assertWallet(user, 0, -10, 1);
    }

    @Test
    @DisplayName("잔액 20의 서로 다른 동시 구매는 두 번 차감하고 두 도안을 해금한다")
    void differentPurchases_WithEnoughBalance_DeductBoth() throws Exception {
        User user = createUser(20, Role.ROLE_USER);
        Pattern first = patternRepository.save(PatternFixture.createPattern());
        Pattern second = patternRepository.save(PatternFixture.createPattern());

        assertThat(concurrently(user,
                current -> purchase(current, first, "yarn"),
                current -> purchase(current, second, "yarn"))).isEmpty();

        assertWallet(user, 0, -20, 2);
    }

    @Test
    @DisplayName("같은 대체 실 동시 구매는 잔액과 거래를 한 번만 차감한다")
    void duplicateYarnPurchase_IsIdempotent() throws Exception {
        User user = createUser(10, Role.ROLE_USER);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());

        assertThat(concurrently(user,
                current -> purchase(current, pattern, "yarn"),
                current -> purchase(current, pattern, "yarn"))).isEmpty();

        assertWallet(user, 0, -10, 1);
        assertThat(userTransactions(user)).hasSize(1);
    }

    @Test
    @DisplayName("같은 채팅 동시 구매는 하나만 배정하고 중복 구매를 잔액 부족과 구분한다")
    void duplicateChatPurchase_ReportsDuplicateWithoutExtraCharge() throws Exception {
        User user = createUser(10, Role.ROLE_USER);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());

        List<Throwable> failures = concurrently(user,
                current -> purchase(current, pattern, "chat"),
                current -> purchase(current, pattern, "chat"));

        assertThat(failures).hasSize(1).allMatch(ChatRoomAlreadyPurchasedException.class::isInstance);
        assertWallet(user, 0, -10, 1);
        assertThat(chatRoomStatusRepository.findAll().stream()
                .filter(status -> status.getUser().getId().equals(user.getId())).count()).isEqualTo(1);
        assertThat(chatRoomStatusRepository.existsByUser_IdAndRoom_Pattern_Id(user.getId(), pattern.getId())).isTrue();
    }

    @Test
    @DisplayName("동시에 읽은 오래된 사용자 정보로 프로필을 수정해도 구매 차감은 유지된다")
    void profileUpdate_OverlappingPurchase_PreservesBalance() throws Exception {
        User user = createUser(100, Role.ROLE_USER);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());
        String nickname = "updated" + user.getId();

        assertThat(concurrently(user,
                current -> purchase(current, pattern, "yarn"),
                current -> userService.updateMyInfo(current, new UpdateMyInfoRequest(nickname, null)))).isEmpty();

        assertWallet(user, 90, -10, 1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("프로필 수정과 초대 보상이 겹쳐도 프로필과 150 보상이 모두 유지된다")
    void profileUpdate_OverlappingReferralBonus_PreservesBothChanges() throws Exception {
        User user = createUser(0, Role.ROLE_USER);
        String nickname = "updated" + user.getId();

        assertThat(concurrently(user,
                current -> creditService.awardReferralBonus(current, 150),
                current -> userService.updateMyInfo(current, new UpdateMyInfoRequest(nickname, null)))).isEmpty();

        assertWallet(user, 150, 150, 0);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("구매와 초대 보상이 겹쳐도 차감과 보상을 모두 반영한다")
    void purchase_OverlappingReferralBonus_PreservesBothAmounts() throws Exception {
        User user = createUser(100, Role.ROLE_USER);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());

        assertThat(concurrently(user,
                current -> purchase(current, pattern, "yarn"),
                current -> creditService.awardReferralBonus(current, 150))).isEmpty();

        assertWallet(user, 240, 140, 1);
    }

    @Test
    @DisplayName("일반 보상이 동시에 지급되어도 일일 20 한도를 넘지 않는다")
    void simultaneousEarnings_RespectDailyCap() throws Exception {
        User user = createUser(0, Role.ROLE_USER);

        assertThat(concurrently(user,
                current -> creditService.addCredits(current, 15, CreditTransactionType.ATTENDANCE_DAILY),
                current -> creditService.addCredits(current, 15, CreditTransactionType.ATTENDANCE_DAILY))).isEmpty();

        assertWallet(user, 20, 20, 0);
        assertThat(userTransactions(user)).extracting(CreditTransaction::getAmount)
                .containsExactlyInAnyOrder(15, 5);
    }

    @Test
    @DisplayName("동시 출석은 출석과 보상을 한 번만 저장한다")
    void simultaneousAttendance_AwardsOnlyOnce() throws Exception {
        User user = createUser(0, Role.ROLE_USER);

        assertThat(concurrently(user, attendanceService::check, attendanceService::check)).isEmpty();

        assertWallet(user, 10, 10, 0);
        assertThat(attendanceRepository.findByUser_IdAndAttendanceDate(user.getId(), java.time.LocalDate.now()))
                .isPresent();
        assertThat(userTransactions(user)).hasSize(1);
    }

    @Test
    @DisplayName("동시 가입 완료는 역할과 프로필을 유지하고 가입 보상을 한 번만 지급한다")
    void simultaneousSignup_AwardsOnlyOnce() throws Exception {
        User guest = createUser(0, Role.ROLE_GUEST);
        SignupRequest request = new SignupRequest("member" + guest.getId(), "defaults/profile.png", List.of());

        assertThat(concurrently(guest,
                current -> authService.signup(current, request),
                current -> authService.signup(current, request))).isEmpty();

        assertWallet(guest, 150, 150, 0);
        User saved = userRepository.findById(guest.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getNickname()).isEqualTo(request.userName());
        assertThat(saved.getReferralCode()).isNotBlank();
        assertThat(userTransactions(guest)).hasSize(1);
    }

    @Test
    @DisplayName("가입과 구매가 겹쳐도 역할·프로필·가입 보상·차감을 모두 보존한다")
    void signup_OverlappingPurchase_PreservesAllChanges() throws Exception {
        User guest = createUser(10, Role.ROLE_GUEST);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());
        SignupRequest request = new SignupRequest("member" + guest.getId(), "defaults/profile.png", List.of());

        assertThat(concurrently(guest,
                current -> authService.signup(current, request),
                current -> purchase(current, pattern, "yarn"))).isEmpty();

        assertWallet(guest, 150, 140, 1);
        User saved = userRepository.findById(guest.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getNickname()).isEqualTo(request.userName());
        assertThat(saved.getReferralCode()).isNotBlank();
        assertThat(userTransactions(guest)).hasSize(2);
    }

    @Test
    @DisplayName("동일 초대 코드 동시 등록은 중복으로 구분하고 양쪽 보상을 한 번씩 지급한다")
    void duplicateReferralRegistration_AwardsBothUsersOnlyOnce() throws Exception {
        User referee = createUser(0, Role.ROLE_USER);
        User referrer = createUser(0, Role.ROLE_USER);
        RegisterReferralCodeRequest request = new RegisterReferralCodeRequest(referrer.getReferralCode());

        List<Throwable> failures = concurrently(referee,
                current -> referralService.registerReferralCode(current, request),
                current -> referralService.registerReferralCode(current, request));

        assertThat(failures).hasSize(1).allMatch(ReferralCodeAlreadyRegisteredException.class::isInstance);
        assertWallet(referee, 150, 150, 0);
        assertWallet(referrer, 150, 150, 0);
        assertThat(userTransactions(referee)).hasSize(1);
        assertThat(userTransactions(referrer)).hasSize(1);
    }

    @Test
    @DisplayName("서로 다른 사용자의 동시 출석도 교착 없이 각자 보상을 저장한다")
    void differentUsers_AttendanceDoesNotDeadlock() throws Exception {
        User first = createUser(0, Role.ROLE_USER);
        User second = createUser(0, Role.ROLE_USER);
        CountDownLatch ready = new CountDownLatch(2);

        assertThat(runTogether(List.of(
                () -> withLoadedUser(first, ready, attendanceService::check),
                () -> withLoadedUser(second, ready, attendanceService::check)))).isEmpty();

        assertWallet(first, 10, 10, 0);
        assertWallet(second, 10, 10, 0);
    }

    @Test
    @DisplayName("서로 다른 사용자의 직접 구매는 빈 해금 인덱스 구간에서도 교착하지 않는다")
    void differentUsers_PurchasesDoNotDeadlock() throws Exception {
        User first = createUser(10, Role.ROLE_USER);
        User second = createUser(10, Role.ROLE_USER);
        Pattern firstPattern = patternRepository.save(PatternFixture.createPattern());
        Pattern secondPattern = patternRepository.save(PatternFixture.createPattern());
        CountDownLatch ready = new CountDownLatch(2);

        assertThat(runTogether(List.of(
                () -> withDetachedUser(first, ready, current -> purchase(current, firstPattern, "yarn")),
                () -> withDetachedUser(second, ready, current -> purchase(current, secondPattern, "yarn")))))
                .isEmpty();

        assertWallet(first, 0, -10, 1);
        assertWallet(second, 0, -10, 1);
    }

    @Test
    @DisplayName("서로 다른 사용자의 직접 보상은 각자 일일 한도를 적용하고 교착하지 않는다")
    void differentUsers_EarningsDoNotDeadlock() throws Exception {
        User first = createUser(0, Role.ROLE_USER);
        User second = createUser(0, Role.ROLE_USER);
        CountDownLatch ready = new CountDownLatch(2);
        Consumer<User> award = current -> creditService.addCredits(current, 25,
                CreditTransactionType.ATTENDANCE_DAILY);

        assertThat(runTogether(List.of(
                () -> withDetachedUser(first, ready, award),
                () -> withDetachedUser(second, ready, award)))).isEmpty();

        assertWallet(first, 20, 20, 0);
        assertWallet(second, 20, 20, 0);
    }

    @Test
    @DisplayName("독립적인 두 초대 등록은 빈 등록 인덱스 구간에서도 교착하지 않는다")
    void disjointReferrals_DoNotDeadlock() throws Exception {
        User firstReferee = createUser(0, Role.ROLE_USER);
        User firstReferrer = createUser(0, Role.ROLE_USER);
        User secondReferee = createUser(0, Role.ROLE_USER);
        User secondReferrer = createUser(0, Role.ROLE_USER);
        CountDownLatch ready = new CountDownLatch(2);

        assertThat(runTogether(List.of(
                () -> withDetachedUser(firstReferee, ready, current -> referralService.registerReferralCode(current,
                        new RegisterReferralCodeRequest(firstReferrer.getReferralCode()))),
                () -> withDetachedUser(secondReferee, ready, current -> referralService.registerReferralCode(current,
                        new RegisterReferralCodeRequest(secondReferrer.getReferralCode())))))).isEmpty();

        for (User user : List.of(firstReferee, firstReferrer, secondReferee, secondReferrer)) {
            assertWallet(user, 150, 150, 0);
            assertThat(userTransactions(user)).hasSize(1);
        }
    }

    @Test
    @DisplayName("오래된 분리 사용자로 초대 코드를 생성해도 최신 잔액과 역할을 덮어쓰지 않는다")
    void detachedReferralCodeAssignment_PreservesLatestUser() {
        User staleUser = createUser(0, Role.ROLE_GUEST);
        creditService.awardReferralBonus(staleUser, 150);

        String code = referralService.ensureReferralCode(staleUser);

        assertWallet(staleUser, 150, 150, 0);
        User saved = userRepository.findById(staleUser.getId()).orElseThrow();
        assertThat(saved.getReferralCode()).isEqualTo(code);
        assertThat(staleUser.getReferralCode()).isEqualTo(code);
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_GUEST);
    }

    @Test
    @DisplayName("초대 보상 뒤 실패하면 등록 이력과 두 사용자 잔액·로그를 모두 취소한다")
    void failedReferral_RollsBackBothUsers() {
        User referee = createUser(0, Role.ROLE_USER);
        User referrer = createUser(0, Role.ROLE_USER);

        assertThatThrownBy(() -> writeTransaction().executeWithoutResult(status -> {
            referralService.registerReferralCode(referee,
                    new RegisterReferralCodeRequest(referrer.getReferralCode()));
            throw new IllegalStateException("보상 후 저장 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertWallet(referee, 0, 0, 0);
        assertWallet(referrer, 0, 0, 0);
        assertThat(userTransactions(referee)).isEmpty();
        assertThat(userTransactions(referrer)).isEmpty();
        assertThat(referralRegistrationRepository.existsByReferee_Id(referee.getId())).isFalse();
    }

    @Test
    @DisplayName("서로를 동시에 초대한 두 사용자의 보상은 교착 없이 모두 반영된다")
    void reciprocalReferrals_UseConsistentLockOrder() throws Exception {
        User first = createUser(0, Role.ROLE_USER);
        User second = createUser(0, Role.ROLE_USER);
        CountDownLatch ready = new CountDownLatch(2);

        List<Throwable> failures = runTogether(List.of(
                () -> withLoadedUser(first, ready, current -> referralService.registerReferralCode(current,
                        new RegisterReferralCodeRequest(second.getReferralCode()))),
                () -> withLoadedUser(second, ready, current -> referralService.registerReferralCode(current,
                        new RegisterReferralCodeRequest(first.getReferralCode())))));

        assertThat(failures).isEmpty();
        assertWallet(first, 300, 300, 0);
        assertWallet(second, 300, 300, 0);
    }

    @Test
    @DisplayName("구매 이후 저장 실패로 트랜잭션이 취소되면 잔액·거래·해금·채팅 참여를 모두 취소한다")
    void failedPurchase_RollsBackAllChanges() {
        User user = createUser(20, Role.ROLE_USER);
        Pattern pattern = patternRepository.save(PatternFixture.createPattern());

        assertThatThrownBy(() -> writeTransaction().executeWithoutResult(status -> {
            purchase(user, pattern, "chat");
            throw new IllegalStateException("구매 후 저장 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertWallet(user, 20, 0, 0);
        assertThat(userTransactions(user)).isEmpty();
        assertThat(chatRoomStatusRepository.existsByUser_IdAndRoom_Pattern_Id(user.getId(), pattern.getId())).isFalse();
    }

    @Test
    @DisplayName("같은 기존 채팅방을 동시에 구매해도 닉네임 순번이 겹치지 않는다")
    void differentUsersPurchasingExistingRoom_AssignDistinctNicknames() throws Exception {
        User first = createUser(10, Role.ROLE_USER);
        User second = createUser(10, Role.ROLE_USER);
        Pattern pattern = patternRepository.saveAndFlush(PatternFixture.createPattern());
        LocalDateTime now = LocalDateTime.now();
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.builder()
                .pattern(pattern)
                .segmentStartAt(now.minusDays(1))
                .segmentEndAt(now.plusDays(6))
                .build());
        CountDownLatch ready = new CountDownLatch(2);

        assertThat(runTogether(List.of(
                () -> withDetachedUser(first, ready, current -> purchase(current, pattern, "chat")),
                () -> withDetachedUser(second, ready, current -> purchase(current, pattern, "chat")))))
                .isEmpty();

        assertWallet(first, 0, -10, 1);
        assertWallet(second, 0, -10, 1);
        assertThat(userTransactions(first)).hasSize(1);
        assertThat(userTransactions(second)).hasSize(1);
        List<ChatRoomStatus> participants = chatRoomStatusRepository.findAll().stream()
                .filter(status -> status.getRoom().getId().equals(room.getId())).toList();
        assertThat(participants).hasSize(2);
        assertThat(participants).extracting(ChatRoomStatus::getNickname)
                .containsExactlyInAnyOrder("레드 코튼", "버건디 린넨");
        assertThat(participants).extracting(status -> status.getUser().getId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    private User createUser(int balance, Role role) {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        return userRepository.saveAndFlush(User.builder()
                .email(identifier + "@example.com")
                .nickname(identifier.substring(0, 16))
                .profileImage("defaults/profile.png")
                .role(role)
                .provider(Provider.GOOGLE)
                .ballBalance(balance)
                .referralCode(role == Role.ROLE_GUEST ? null : "UFO" + identifier.substring(0, 6))
                .build());
    }

    private void purchase(User user, Pattern pattern, String type) {
        purchaseService.purchase(user, pattern.getId(), new PatternPurchaseRequest(type));
    }

    private void assertWallet(User user, int balance, int transactionSum, int unlockCount) {
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBallBalance()).isEqualTo(balance);
        assertThat(userTransactions(user).stream().mapToInt(CreditTransaction::getAmount).sum())
                .isEqualTo(transactionSum);
        assertThat(unlockRepository.findAll().stream()
                .filter(unlock -> unlock.getUser().getId().equals(user.getId())).count()).isEqualTo(unlockCount);
    }

    private List<CreditTransaction> userTransactions(User user) {
        return transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getUser().getId().equals(user.getId())).toList();
    }

    @SafeVarargs
    private final List<Throwable> concurrently(User user, Consumer<User>... actions) throws Exception {
        CountDownLatch ready = new CountDownLatch(actions.length);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Consumer<User> action : actions) {
            tasks.add(() -> withLoadedUser(user, ready, action));
        }
        return runTogether(tasks);
    }

    private Void withLoadedUser(User user, CountDownLatch ready, Consumer<User> action) {
        writeTransaction().executeWithoutResult(status -> {
            User staleUser = userRepository.findById(user.getId()).orElseThrow();
            ready.countDown();
            await(ready);
            action.accept(staleUser);
        });
        return null;
    }

    private Void withDetachedUser(User user, CountDownLatch ready, Consumer<User> action) {
        User staleUser = userRepository.findById(user.getId()).orElseThrow();
        ready.countDown();
        await(ready);
        // No outer transaction: exercise the production service's declared isolation boundary.
        action.accept(staleUser);
        return null;
    }

    private TransactionTemplate writeTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Mirror writer boundaries while deliberately preloading a stale managed user before each call.
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private List<Throwable> runTogether(List<Callable<Void>> tasks) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failures.add(exception.getCause());
                }
            }
        }
        return failures;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("동시 요청 준비 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({UserService.class, CreditService.class, PatternPurchaseService.class,
            ChatRoomProvisioningService.class, ChatNicknameGenerator.class, AttendanceService.class,
            ReferralService.class, ReferralCodeGenerator.class,
            InterestService.class, AuthService.class})
    static class TestConfig {

        @Bean
        ImageService imageService() {
            ImageService service = mock(ImageService.class);
            when(service.buildImageUrl("defaults/profile.png")).thenReturn("https://cdn.test/defaults/profile.png");
            return service;
        }

        @Bean
        ReferralCodeProperties referralCodeProperties() {
            return new ReferralCodeProperties("credit-concurrency-test-secret-32-bytes");
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider("Y3JlZGl0LWNvbmN1cnJlbmN5LXRlc3Qta2V5LTMyLWJ5dGVz");
        }
    }
}
