package com.ufo.ufo.domain.interest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.interest.dao.UserInterestRepository;
import com.ufo.ufo.domain.interest.domain.UserInterest;
import com.ufo.ufo.domain.interest.dto.request.UpdateMyInterestsRequest;
import com.ufo.ufo.domain.interest.dto.response.MyInterestsResponse;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidInterestKeywordException;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import com.ufo.ufo.support.fixture.UserInterestFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관심사 서비스 테스트")
class InterestServiceTest {

    @Mock
    private UserInterestRepository userInterestRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private InterestService interestService;

    @Test
    @DisplayName("내 관심사 조회 시 사용자에게 저장된 키워드 목록을 반환해야 한다")
    void getMyInterests_ReturnsKeywordsForUser() {
        User user = UserFixture.createUserWithId(1L);
        when(userInterestRepository.findAllByUser_Id(1L)).thenReturn(List.of(
                UserInterestFixture.createUserInterest(user, "빈티지"),
                UserInterestFixture.createUserInterest(user, "캐주얼")
        ));

        MyInterestsResponse response = interestService.getMyInterests(user);

        assertThat(response.keywords()).containsExactly("빈티지", "캐주얼");
    }

    @Test
    @DisplayName("내 관심사 수정 시 키워드를 정규화하고 교체 저장하되 사용자 역할은 변경하지 않아야 한다")
    void updateMyInterests_NormalizesAndReplacesWithoutPromotingRole() {
        User user = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUser("test@example.com", Role.ROLE_GUEST);
        UserFixture.setId(loginUser, 1L);
        UpdateMyInterestsRequest request = new UpdateMyInterestsRequest(List.of(" 빈티지 ", "캐주얼", "빈티지", " "));
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(userInterestRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        MyInterestsResponse response = interestService.updateMyInterests(user, request);

        assertThat(response.keywords()).containsExactly("빈티지", "캐주얼");
        assertThat(loginUser.getRole()).isEqualTo(Role.ROLE_GUEST);
        verify(userInterestRepository).deleteAllByUser_Id(1L);

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(userInterestRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(UserInterest::getKeyword)
                .containsExactly("빈티지", "캐주얼");
    }

    @Test
    @DisplayName("유효하지 않은 관심사 키워드가 포함되면 예외가 발생해야 한다")
    void updateMyInterests_InvalidKeyword_ThrowsApiException() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(user);
        UpdateMyInterestsRequest request = new UpdateMyInterestsRequest(List.of("invalid-keyword"));

        assertThatThrownBy(() -> interestService.updateMyInterests(user, request))
                .isInstanceOf(InvalidInterestKeywordException.class);
    }
}
