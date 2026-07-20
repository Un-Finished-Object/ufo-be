package com.ufo.ufo.domain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 구매 프로젝트 서비스 테스트")
class UserProjectServiceTest {

    @Mock
    private UnlockRepository unlockRepository;

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private UserProjectService userProjectService;

    @Test
    @DisplayName("대체 실과 채팅방 구매 내역을 도안 기준으로 병합해 반환해야 한다")
    void getPurchasedProjects_MergesYarnAndChatPurchases() {
        User user = UserFixture.createUserWithId(1L);
        Pattern bothPurchasedPattern = PatternFixture.createPatternWithId(10L);
        Pattern chatOnlyPattern = PatternFixture.createPatternWithId(20L);
        Unlock yarnUnlock = yarnUnlock(user, 10L, LocalDateTime.of(2026, 4, 1, 10, 0));
        ChatRoomStatus bothChatStatus = chatStatus(
                user,
                ChatRoomFixture.createRoomWithId(bothPurchasedPattern, 100L),
                LocalDateTime.of(2026, 4, 2, 10, 0)
        );
        ChatRoomStatus chatOnlyStatus = chatStatus(
                user,
                ChatRoomFixture.createRoomWithId(chatOnlyPattern, 200L),
                LocalDateTime.of(2026, 4, 3, 10, 0)
        );
        when(unlockRepository.findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(1L, UnlockType.YARN_INFO))
                .thenReturn(List.of(yarnUnlock));
        when(patternRepository.findAllById(List.of(10L))).thenReturn(List.of(bothPurchasedPattern));
        when(chatRoomStatusRepository.findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(chatOnlyStatus, bothChatStatus));
        when(imageService.buildImageUrl("./patterns/1.png"))
                .thenReturn("https://cdn.example.com/patterns/1.png");

        PurchasedProjectsResponse response = userProjectService.getPurchasedProjects(user, 1);

        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.projects()).hasSize(2);
        assertThat(response.projects().getFirst().patternId()).isEqualTo(20L);
        assertThat(response.projects().getFirst().purchaseYarn()).isFalse();
        assertThat(response.projects().getFirst().purchaseChat()).isTrue();
        assertThat(response.projects().getFirst().purchaseChatId()).isEqualTo(200L);
        assertThat(response.projects().getFirst().thumbnailUrl())
                .isEqualTo("https://cdn.example.com/patterns/1.png");
        assertThat(response.projects().get(1).patternId()).isEqualTo(10L);
        assertThat(response.projects().get(1).purchaseYarn()).isTrue();
        assertThat(response.projects().get(1).purchaseChat()).isTrue();
        assertThat(response.projects().get(1).purchaseChatId()).isEqualTo(100L);
        assertThat(response.projects().get(1).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/patterns/1.png");
    }

    @Test
    @DisplayName("삭제된 도안의 대체 실 구매 내역은 제외해야 한다")
    void getPurchasedProjects_DeletedYarnPattern_Excluded() {
        User user = UserFixture.createUserWithId(1L);
        Unlock yarnUnlock = yarnUnlock(user, 10L, LocalDateTime.of(2026, 4, 1, 10, 0));
        Pattern deletedPattern = PatternFixture.createPatternWithId(10L);
        PatternFixture.setDeletedAt(deletedPattern, LocalDateTime.of(2026, 4, 2, 10, 0));
        when(unlockRepository.findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(1L, UnlockType.YARN_INFO))
                .thenReturn(List.of(yarnUnlock));
        when(patternRepository.findAllById(List.of(10L))).thenReturn(List.of(deletedPattern));
        when(chatRoomStatusRepository.findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of());

        PurchasedProjectsResponse response = userProjectService.getPurchasedProjects(user, 1);

        assertThat(response.projects()).isEmpty();
        assertThat(response.nextPage()).isEqualTo(0);
    }

    @Test
    @DisplayName("구매 프로젝트 목록은 10개씩 페이지네이션하고 남은 페이지 수를 최대 5로 반환해야 한다")
    void getPurchasedProjects_PaginatesByTenAndLimitsNextPage() {
        User user = UserFixture.createUserWithId(1L);
        List<Unlock> unlocks = java.util.stream.LongStream.rangeClosed(1, 62)
                .mapToObj(patternId -> yarnUnlock(
                        user,
                        patternId,
                        LocalDateTime.of(2026, 4, 1, 10, 0).plusDays(patternId)
                ))
                .toList();
        List<Pattern> patterns = java.util.stream.LongStream.rangeClosed(1, 62)
                .mapToObj(PatternFixture::createPatternWithId)
                .toList();
        List<Long> patternIds = java.util.stream.LongStream.rangeClosed(1, 62).boxed().toList();
        when(unlockRepository.findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(1L, UnlockType.YARN_INFO))
                .thenReturn(unlocks);
        when(patternRepository.findAllById(patternIds)).thenReturn(patterns);
        when(chatRoomStatusRepository.findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of());

        PurchasedProjectsResponse response = userProjectService.getPurchasedProjects(user, 1);

        assertThat(response.projects()).hasSize(10);
        assertThat(response.projects().getFirst().patternId()).isEqualTo(62L);
        assertThat(response.nextPage()).isEqualTo(5);
    }

    @Test
    @DisplayName("매우 큰 page 값이 들어와도 overflow 없이 빈 목록을 반환해야 한다")
    void getPurchasedProjects_VeryLargePage_ReturnsEmptyWithoutOverflow() {
        User user = UserFixture.createUserWithId(1L);
        List<Unlock> unlocks = java.util.stream.LongStream.rangeClosed(1, 2)
                .mapToObj(patternId -> yarnUnlock(
                        user,
                        patternId,
                        LocalDateTime.of(2026, 4, 1, 10, 0).plusDays(patternId)
                ))
                .toList();
        List<Pattern> patterns = java.util.stream.LongStream.rangeClosed(1, 2)
                .mapToObj(PatternFixture::createPatternWithId)
                .toList();
        List<Long> patternIds = java.util.stream.LongStream.rangeClosed(1, 2).boxed().toList();
        when(unlockRepository.findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(1L, UnlockType.YARN_INFO))
                .thenReturn(unlocks);
        when(patternRepository.findAllById(patternIds)).thenReturn(patterns);
        when(chatRoomStatusRepository.findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of());

        PurchasedProjectsResponse response = userProjectService.getPurchasedProjects(user, 300000000);

        assertThat(response.projects()).isEmpty();
        assertThat(response.nextPage()).isEqualTo(0);
    }

    private Unlock yarnUnlock(User user, Long patternId, LocalDateTime createdAt) {
        Unlock unlock = Unlock.builder()
                .user(user)
                .patternId(patternId)
                .type(UnlockType.YARN_INFO)
                .build();
        setCreatedAt(unlock, createdAt);
        return unlock;
    }

    private ChatRoomStatus chatStatus(User user, ChatRoom room, LocalDateTime createdAt) {
        ChatRoomStatus status = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();
        setCreatedAt(status, createdAt);
        return status;
    }

    private void setCreatedAt(Object entity, LocalDateTime createdAt) {
        try {
            Field createdAtField = entity.getClass().getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(entity, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
