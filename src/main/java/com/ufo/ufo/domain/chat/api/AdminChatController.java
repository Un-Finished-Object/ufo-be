package com.ufo.ufo.domain.chat.api;

import com.ufo.ufo.domain.chat.application.AdminChatService;
import com.ufo.ufo.domain.chat.dto.response.AdminCheckChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminDeleteChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomListResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/chats")
public class AdminChatController {

    private final AdminChatService adminChatService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUnreadChatRoomListResponse>> getUnreadChatRooms(
            @LoginUser User user,
            @RequestParam Integer page
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.getUnreadChatRooms(user, page)));
    }

    @DeleteMapping("/{chatRoomId}/messages/{messageId}")
    public ResponseEntity<ApiResponse<AdminDeleteChatMessageResponse>> deleteMessage(
            @LoginUser User user,
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.deleteMessage(user, chatRoomId, messageId)));
    }

    @PostMapping("/{chatRoomId}/messages/{messageId}/check")
    public ResponseEntity<ApiResponse<AdminCheckChatMessageResponse>> checkMessage(
            @LoginUser User user,
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.checkMessage(user, chatRoomId, messageId)));
    }
}
