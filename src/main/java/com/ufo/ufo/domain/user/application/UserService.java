package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.request.UpdateMyInfoRequest;
import com.ufo.ufo.domain.user.dto.response.UpdateMyInfoResponse;
import com.ufo.ufo.domain.user.dto.response.NicknameExistsResponse;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.domain.user.event.ProfileImageChangedEvent;
import com.ufo.ufo.domain.user.exception.DuplicateNicknameException;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.global.exception.UserNotFoundException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher eventPublisher;

    public UserResponse getUserInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user, imageService.buildImageUrl(user.getProfileImage()));
    }

    public UserResponse getMyInfo(User user) {
        return UserResponse.from(user, imageService.buildImageUrl(user.getProfileImage()));
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    public NicknameExistsResponse checkNicknameExists(String nickname) {
        String normalizedNickname = nickname.trim();
        return NicknameExistsResponse.from(userRepository.existsByNickname(normalizedNickname));
    }

    @Transactional
    public UpdateMyInfoResponse updateMyInfo(User user, UpdateMyInfoRequest request) {
        User updatedUser = updateNameAndProfileImage(user, request.userName(), request.profileImageKey());
        return UpdateMyInfoResponse.from(updatedUser, imageService.buildImageUrl(updatedUser.getProfileImage()));
    }

    @Transactional
    public User updateNameAndProfileImage(User user, String userName, String profileImageKey) {
        User loginUser = getUserById(user.getId());
        String updatedUserName = userName == null ? loginUser.getNickname() : userName;
        if (userName != null
                && userRepository.existsByNicknameAndIdNot(userName, loginUser.getId())) {
            throw new DuplicateNicknameException();
        }
        String previousProfileImage = loginUser.getProfileImage();
        String profileImage = previousProfileImage;
        if (profileImageKey != null) {
            imageService.validateProfileImageKey(loginUser, profileImageKey);
            profileImage = profileImageKey;
        }
        loginUser.updateNameAndProfileImage(updatedUserName, profileImage);
        if (userName != null) {
            try {
                userRepository.flush();
            } catch (DataIntegrityViolationException exception) {
                throw new DuplicateNicknameException();
            }
        }
        if (!Objects.equals(profileImage, previousProfileImage)) {
            eventPublisher.publishEvent(new ProfileImageChangedEvent(previousProfileImage, profileImage));
        }
        return loginUser;
    }
}
