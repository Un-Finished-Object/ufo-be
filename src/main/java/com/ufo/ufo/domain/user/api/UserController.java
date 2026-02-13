package com.ufo.ufo.domain.user.api;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.global.security.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("v1/users")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(@LoginUser User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
