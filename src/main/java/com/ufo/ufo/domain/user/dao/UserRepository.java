package com.ufo.ufo.domain.user.dao;

import com.ufo.ufo.domain.user.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Modifying
    @Query("update User u set u.ballBalance = u.ballBalance + :amount where u.id = :userId")
    int incrementBallBalance(@Param("userId") Long userId, @Param("amount") int amount);

    Optional<User> findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
