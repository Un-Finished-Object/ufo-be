package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.request.UpdateMyInfoRequest;
import com.ufo.ufo.domain.user.dto.response.UpdateMyInfoResponse;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ImageService imageService;

    public UserResponse getUserInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public UpdateMyInfoResponse updateMyInfo(User user, UpdateMyInfoRequest request) {
        User loginUser = getUserById(user.getId());
        String userName = request.userName() == null ? loginUser.getNickname() : request.userName();
        String profileImage = loginUser.getProfileImage();
        if (request.profileImage() != null) {
            imageService.validateProfileImage(loginUser, request.profileImage());
            profileImage = request.profileImage();
        }
        loginUser.updateNameAndProfileImage(userName, profileImage);
        return UpdateMyInfoResponse.from(loginUser);
    }
}
