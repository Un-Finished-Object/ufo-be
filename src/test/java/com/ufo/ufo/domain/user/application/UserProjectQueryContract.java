package com.ufo.ufo.domain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse.Project;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class UserProjectQueryContract {

    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2026, 4, 1, 10, 0);

    @Autowired
    private UserProjectService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ImageService imageService;

    private User user;
    private int roomSequence;

    @BeforeEach
    void setUp() {
        user = saveUser("owner@example.com", "owner");
        when(imageService.buildImageUrl(anyString())).thenAnswer(invocation ->
                "https://cdn.example.com/" + invocation.getArgument(0, String.class));
    }

    @AfterEach
    void clearQueries() {
        ProjectQueryInspector.QUERIES.remove();
    }

    @Test
    @DisplayName("대체 실 단독, 채팅 단독, 양쪽 구매를 도안별로 합치고 최신 구매일로 정렬해야 한다")
    void mergesPurchasesAndPreservesResponseFields() {
        Pattern both = savePattern("both");
        Pattern chatOnly = savePattern("chat");
        Pattern yarnOnly = savePattern("yarn");
        saveUnlock(user, both, UnlockType.YARN_INFO, BASE_DATE);
        ChatRoom bothRoom = saveRoom(both);
        saveChat(user, bothRoom, BASE_DATE.plusDays(1));
        ChatRoom chatRoom = saveRoom(chatOnly);
        saveChat(user, chatRoom, BASE_DATE.plusDays(2));
        saveUnlock(user, yarnOnly, UnlockType.YARN_INFO, BASE_DATE.plusDays(3));
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, 1);

        assertThat(response.projects()).extracting(Project::patternId)
                .containsExactly(yarnOnly.getId(), chatOnly.getId(), both.getId());
        assertThat(response.projects().getFirst()).isEqualTo(new Project(
                yarnOnly.getId(), "yarn", "artist", "https://cdn.example.com/patterns/yarn.png",
                true, BASE_DATE.plusDays(3), false, null, null));
        assertThat(response.projects().get(1)).isEqualTo(new Project(
                chatOnly.getId(), "chat", "artist", "https://cdn.example.com/patterns/chat.png",
                false, null, true, chatRoom.getId(), BASE_DATE.plusDays(2)));
        assertThat(response.projects().get(2)).isEqualTo(new Project(
                both.getId(), "both", "artist", "https://cdn.example.com/patterns/both.png",
                true, BASE_DATE, true, bothRoom.getId(), BASE_DATE.plusDays(1)));
        assertThat(response.nextPage()).isZero();
    }

    @Test
    @DisplayName("참여 시각이 같으면 참여 ID가 큰 방을 선택하고 도안도 ID 내림차순으로 정렬해야 한다")
    void breaksTiesByParticipationIdAndPatternId() {
        Pattern first = savePattern("first");
        Pattern second = savePattern("second");
        ChatRoom lowerRoom = saveRoom(first);
        ChatRoom higherRoom = saveRoom(first);
        saveChat(user, higherRoom, BASE_DATE);
        saveChat(user, lowerRoom, BASE_DATE);
        saveUnlock(user, second, UnlockType.YARN_INFO, BASE_DATE);
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, 1);

        assertThat(response.projects()).extracting(Project::patternId)
                .containsExactly(second.getId(), first.getId());
        assertThat(response.projects().get(1).purchaseChatId()).isEqualTo(lowerRoom.getId());
        assertThat(response.projects().get(1).purchaseChatDate()).isEqualTo(BASE_DATE);
    }

    @Test
    @DisplayName("최신 채팅 참여를 선택하고 더 최근의 대체 실 구매일도 정렬에 반영해야 한다")
    void selectsLatestParticipationAndSortsByTheLaterPurchase() {
        Pattern first = savePattern("first");
        Pattern second = savePattern("second");
        ChatRoom latest = saveRoom(first);
        ChatRoom older = saveRoom(first);
        saveChat(user, latest, BASE_DATE.plusDays(2));
        saveChat(user, older, BASE_DATE);
        saveUnlock(user, first, UnlockType.YARN_INFO, BASE_DATE.plusDays(4));
        saveUnlock(user, second, UnlockType.YARN_INFO, BASE_DATE.plusDays(3));
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, 1);

        assertThat(response.projects()).extracting(Project::patternId)
                .containsExactly(first.getId(), second.getId());
        assertThat(response.projects().getFirst().purchaseChatId()).isEqualTo(latest.getId());
        assertThat(response.projects().getFirst().purchaseChatDate()).isEqualTo(BASE_DATE.plusDays(2));
    }

    @Test
    @DisplayName("삭제 도안, 다른 사용자 구매, 참여 없는 CHAT 해금은 목록과 총 개수에서 제외해야 한다")
    void excludesDeletedAndOtherUsersPurchasesFromRowsAndCount() {
        User other = saveUser("other@example.com", "other");
        List<Pattern> visible = saveYarnProjects(10);
        Pattern deleted = savePattern("deleted");
        PatternFixture.setDeletedAt(deleted, BASE_DATE);
        saveUnlock(user, deleted, UnlockType.YARN_INFO, BASE_DATE);
        saveChat(user, saveRoom(deleted), BASE_DATE);
        saveUnlock(other, savePattern("other-yarn"), UnlockType.YARN_INFO, BASE_DATE);
        saveChat(other, saveRoom(savePattern("other-chat")), BASE_DATE);
        saveUnlock(user, savePattern("unlock-only"), UnlockType.CHAT, BASE_DATE);
        saveChat(user, saveRoom(visible.getFirst()), BASE_DATE.plusDays(1));
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, 1);

        assertThat(response.projects()).hasSize(10);
        assertThat(response.projects()).extracting(Project::patternId)
                .containsExactlyInAnyOrderElementsOf(visible.stream().map(Pattern::getId).toList());
        assertThat(response.nextPage()).isZero();
        assertThat(service.getPurchasedProjects(user, 2).projects()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,1,0,0", "1,1,1,0", "10,1,10,0", "11,1,10,1", "11,2,1,0",
            "20,2,10,0", "21,2,10,1", "62,1,10,5", "62,6,10,1", "62,7,2,0", "62,8,0,0"})
    @DisplayName("도안 수로 페이지를 계산하고 현재 페이지의 항목만 DB에서 읽어야 한다")
    void paginatesDistinctProjectsAtTheDatabase(int size, int page, int expectedSize, int nextPage) {
        List<Pattern> patterns = saveYarnProjects(size);
        for (Pattern pattern : patterns) {
            saveChat(user, saveRoom(pattern), BASE_DATE);
        }
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, page);

        assertThat(response.projects()).hasSize(expectedSize);
        assertThat(response.nextPage()).isEqualTo(nextPage);
        if (expectedSize > 0) {
            int firstIndex = size - 1 - (page - 1) * 10;
            List<Long> expectedIds = new ArrayList<>();
            for (int index = firstIndex; index > firstIndex - expectedSize; index--) {
                expectedIds.add(patterns.get(index).getId());
            }
            assertThat(response.projects()).extracting(Project::patternId).containsExactlyElementsOf(expectedIds);
            assertThat(ProjectQueryInspector.QUERIES.get()).hasSize(2);
            assertThat(ProjectQueryInspector.QUERIES.get()).anySatisfy(sql ->
                    assertThat(sql).contains("limit", "offset"));
            verify(imageService, times(expectedSize)).buildImageUrl(anyString());
        } else {
            assertThat(ProjectQueryInspector.QUERIES.get()).hasSize(1);
        }
        assertThat(entityManager.getEntityManagerFactory().unwrap(SessionFactory.class)
                .getStatistics().getEntityLoadCount()).isZero();
    }

    @Test
    @DisplayName("Integer 최대 페이지도 overflow나 범위를 넘는 DB 조회 없이 빈 목록을 반환해야 한다")
    void handlesVeryLargePage() {
        saveYarnProjects(2);
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, Integer.MAX_VALUE);

        assertThat(response.projects()).isEmpty();
        assertThat(response.nextPage()).isZero();
        assertThat(ProjectQueryInspector.QUERIES.get()).hasSize(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("페이지가 없거나 1 미만이면 첫 페이지를 반환해야 한다")
    void normalizesInvalidPages(Integer page) {
        Pattern pattern = saveYarnProjects(1).getFirst();
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, page);

        assertThat(response.projects()).extracting(Project::patternId).containsExactly(pattern.getId());
        assertThat(response.nextPage()).isZero();
    }

    @Test
    @DisplayName("숨긴 채팅방과 가입 구간이 지난 채팅방 구매도 기존처럼 프로젝트에 포함해야 한다")
    void includesHiddenAndPastSegmentParticipations() {
        Pattern pattern = savePattern("past");
        ChatRoom room = saveRoom(pattern);
        ChatRoomStatus status = saveChat(user, room, BASE_DATE);
        status.update(null, true);
        prepareQuery();

        PurchasedProjectsResponse response = service.getPurchasedProjects(user, 1);

        assertThat(response.projects()).extracting(Project::patternId).containsExactly(pattern.getId());
        assertThat(response.projects().getFirst().purchaseChatId()).isEqualTo(room.getId());
    }

    private List<Pattern> saveYarnProjects(int size) {
        List<Pattern> patterns = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Pattern pattern = savePattern("project-" + index);
            saveUnlock(user, pattern, UnlockType.YARN_INFO, BASE_DATE.plusDays(index));
            patterns.add(pattern);
        }
        return patterns;
    }

    private User saveUser(String email, String nickname) {
        User saved = UserFixture.createUser(email, Role.ROLE_USER);
        saved.updateNameAndProfileImage(nickname, saved.getProfileImage());
        entityManager.persist(saved);
        return saved;
    }

    private Pattern savePattern(String name) {
        Pattern pattern = PatternFixture.createPattern(name, "artist", "apparel", "long_sweater",
                "patterns/" + name + ".png");
        entityManager.persist(pattern);
        return pattern;
    }

    private void saveUnlock(User owner, Pattern pattern, UnlockType type, LocalDateTime date) {
        Unlock unlock = Unlock.builder().user(owner).patternId(pattern.getId()).type(type).build();
        entityManager.persist(unlock);
        entityManager.flush();
        jdbc.update("update unlocks set created_at = ? where unlock_id = ?",
                Timestamp.valueOf(date), unlock.getId());
    }

    private ChatRoom saveRoom(Pattern pattern) {
        LocalDateTime start = BASE_DATE.plusSeconds(roomSequence++);
        ChatRoom room = ChatRoom.builder().pattern(pattern)
                .segmentStartAt(start).segmentEndAt(start.plusDays(7)).build();
        entityManager.persist(room);
        return room;
    }

    private ChatRoomStatus saveChat(User owner, ChatRoom room, LocalDateTime date) {
        ChatRoomStatus status = ChatRoomStatus.builder().user(owner).room(room)
                .nickname("user-" + owner.getId()).build();
        entityManager.persist(status);
        entityManager.flush();
        jdbc.update("update chat_room_statuses set created_at = ? where chat_room_status_id = ?",
                Timestamp.valueOf(date), status.getId());
        return status;
    }

    private void prepareQuery() {
        entityManager.flush();
        entityManager.clear();
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics().clear();
        ProjectQueryInspector.QUERIES.get().clear();
    }

    public static class ProjectQueryInspector implements StatementInspector {

        private static final ThreadLocal<List<String>> QUERIES = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.startsWith("select ") || normalized.startsWith("with ")) {
                QUERIES.get().add(normalized);
            }
            return sql;
        }
    }
}
