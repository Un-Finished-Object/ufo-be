package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long>,
        JpaSpecificationExecutor<CreditTransaction> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from CreditTransaction c
            where c.user.id = :userId
              and c.amount > 0
              and c.type not in (
                  com.ufo.ufo.domain.credit.domain.CreditTransactionType.SIGNUP_BONUS,
                  com.ufo.ufo.domain.credit.domain.CreditTransactionType.REFERRAL_BONUS
              )
              and c.createdAt >= :from
              and c.createdAt < :to
            """)
    List<CreditTransaction> findDailyEarningsForUpdate(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
