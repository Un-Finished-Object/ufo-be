package com.ufo.ufo.domain.user.api;

import com.ufo.ufo.domain.user.dto.response.UpdateMyInfoResponse;
import com.ufo.ufo.domain.user.dto.response.NicknameExistsResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ufo.ufo.domain.chat.application.ChatRoomQueryService;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse;
import com.ufo.ufo.domain.user.application.UserProjectService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("User 컨트롤러 파라미터 검증 테스트")
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterestService interestService;

    @MockitoBean
    private ScrapService scrapService;

    @MockitoBean
    private ChatRoomQueryService chatRoomQueryService;

    @MockitoBean
    private UserProjectService userProjectService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("닉네임 중복 확인은 exists 값을 반환해야 한다")
    void checkNicknameExists_ValidNickname_ReturnsExists() throws Exception {
        when(userService.checkNicknameExists("뜨개러"))
                .thenReturn(new NicknameExistsResponse(true));

        mockMvc.perform(get("/v1/users/nicknames/{nickname}/check", "뜨개러"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("닉네임 중복 확인에서 2자 미만 닉네임은 400을 반환해야 한다")
    void checkNicknameExists_TooShortNickname_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/users/nicknames/{nickname}/check", "실"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("nickname은 2자 이상 20자 이하여야 합니다."));
    }

    @Test
    @DisplayName("내 찜 목록 조회에서 page를 생략하면 1로 기본 처리해야 한다")
    void getMyScraps_MissingPage_UsesDefaultPageOne() throws Exception {
        when(scrapService.getMyScraps(any(), eq(1)))
                .thenReturn(MyScrapsResponse.from(List.of(), 1, 0));

        mockMvc.perform(get("/v1/users/me/scraps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(scrapService).getMyScraps(any(), eq(1));
    }

    @Test
    @DisplayName("내 찜 목록 조회에서 page가 숫자가 아니면 400을 반환해야 한다")
    void getMyScraps_NonNumericPage_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/users/me/scraps").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page 파라미터 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("내 채팅방 목록 조회에서 page를 생략하면 1로 기본 처리해야 한다")
    void getMyChats_MissingPage_UsesDefaultPageOne() throws Exception {
        when(chatRoomQueryService.getMyChats(any(), eq(1)))
                .thenReturn(UserChatRoomListResponse.of(List.of(), 1, 0));

        mockMvc.perform(get("/v1/users/me/chats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.nextPage").value(0))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(chatRoomQueryService).getMyChats(any(), eq(1));
    }

    @Test
    @DisplayName("내 채팅방 목록 조회에서 page가 1 미만이면 400을 반환해야 한다")
    void getMyChats_InvalidPage_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/users/me/chats").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page는 1 이상이어야 합니다."));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("내 정보 수정에서 userName을 생략하면 200을 반환해야 한다")
    void updateMyInfo_MissingUserName_ReturnsOk() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(UserFixture.createUser("test@example.com", Role.ROLE_USER)));
        when(userService.updateMyInfo(any(), any()))
                .thenReturn(new UpdateMyInfoResponse(10L, "tester", "https://example.com/profile.png"));

        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageKey": "profiles/10/profile.png"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("내 정보 수정에서 userName에 공백만 입력하면 400을 반환해야 한다")
    void updateMyInfo_BlankUserName_ReturnsBadRequest() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(UserFixture.createUser("test@example.com", Role.ROLE_USER)));

        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "  ",
                                  "profileImageKey": "profiles/10/profile.png"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("userName 필드의 정보가 올바르지 않습니다."));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("내 정보 수정에서 profileImageKey에 공백만 입력하면 400을 반환해야 한다")
    void updateMyInfo_BlankProfileImage_ReturnsBadRequest() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(UserFixture.createUser("test@example.com", Role.ROLE_USER)));

        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "updatedName",
                                  "profileImageKey": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("profileImageKey 필드의 정보가 올바르지 않습니다."));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("내 정보 수정에서 userName과 profileImageKey를 모두 생략하면 200을 반환해야 한다")
    void updateMyInfo_MissingUserNameAndProfileImage_ReturnsOk() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(UserFixture.createUser("test@example.com", Role.ROLE_USER)));
        when(userService.updateMyInfo(any(), any()))
                .thenReturn(new UpdateMyInfoResponse(10L, "tester", "https://example.com/profile.png"));

        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("내 정보 수정에서 profileImageKey를 생략하면 200을 반환해야 한다")
    void updateMyInfo_MissingProfileImage_ReturnsOk() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(UserFixture.createUser("test@example.com", Role.ROLE_USER)));

        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "updatedName"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
