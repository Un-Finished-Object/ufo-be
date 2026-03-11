package com.ufo.ufo.domain.user.api;

import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.interest.dto.request.UpdateMyInterestsRequest;
import com.ufo.ufo.domain.interest.dto.response.MyInterestsResponse;
import com.ufo.ufo.domain.chat.application.ChatRoomQueryService;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("v1/users")
@RequiredArgsConstructor
public class UserController {

    private final InterestService interestService;
    private final ScrapService scrapService;
    private final ChatRoomQueryService chatRoomQueryService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/me/interests")
    public ResponseEntity<ApiResponse<MyInterestsResponse>> getMyInterests(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(interestService.getMyInterests(user)));
    }

    @GetMapping("/me/scraps")
    public ResponseEntity<ApiResponse<MyScrapsResponse>> getMyScraps(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(scrapService.getMyScraps(user)));
    }

    @GetMapping("/me/chats")
    public ResponseEntity<ApiResponse<UserChatRoomListResponse>> getMyChats(@LoginUser User user) {
        return ResponseEntity.ok(ApiResponse.success(chatRoomQueryService.getMyChats(user)));
    }

    @PatchMapping("/me/interests")
    public ResponseEntity<ApiResponse<MyInterestsResponse>> updateMyInterests(
            @LoginUser User user,
            @RequestBody @Valid UpdateMyInterestsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interestService.updateMyInterests(user, request)));
    }
}
