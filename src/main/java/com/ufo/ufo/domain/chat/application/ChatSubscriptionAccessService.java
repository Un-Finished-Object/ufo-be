package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSubscriptionAccessService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;

    public boolean canSubscribe(Principal principal, Long roomId) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()
                || roomId == null || roomId <= 0) {
            return false;
        }
        User user = userRepository.findByEmail(principal.getName())
                .filter(found -> found.getDeletedAt() == null)
                .orElse(null);
        if (user == null || !chatRoomRepository.existsByIdAndPattern_DeletedAtIsNull(roomId)) {
            return false;
        }
        if (user.getRole() == Role.ROLE_ADMIN && principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> Role.ROLE_ADMIN.name().equals(authority.getAuthority()))) {
            return true;
        }
        return chatRoomStatusRepository.findByUser_IdAndRoom_Id(user.getId(), roomId).isPresent();
    }
}
