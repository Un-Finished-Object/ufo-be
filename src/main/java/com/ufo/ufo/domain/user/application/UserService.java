package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getUserInfo(String email) {
        User user = userRepository.findByEmail(email).
                orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }
}
