package com.ufo.ufo.domain.user.dao;

import com.ufo.ufo.domain.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
