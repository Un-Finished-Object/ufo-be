package com.ufo.ufo.domain.chat.api;

import com.ufo.ufo.domain.chat.application.ChatMessageService;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/{patternId}/messages")
    public ResponseEntity<ApiResponse<ChatMessagesResponse>> getMessages(
            @LoginUser User user,
            @PathVariable Long patternId,
            @RequestParam(required = false) Long messageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(chatMessageService.getMessages(user, patternId, messageId)));
    }
}
