package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long>,
        JpaSpecificationExecutor<CreditTransaction> {

    @Query("""
            select coalesce(sum(c.amount), 0)
            from CreditTransaction c
            where c.user.id = :userId
              and c.amount > 0
              and c.type <> com.ufo.ufo.domain.credit.domain.CreditTransactionType.REFERRAL_BONUS
              and c.createdAt >= :from
              and c.createdAt < :to
            """)
    int sumPositiveAmountByUserAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
