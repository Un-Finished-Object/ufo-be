package com.ufo.ufo.domain.credit.dao;

import com.ufo.ufo.domain.credit.dao.projection.CreditTransactionHistory;
import com.ufo.ufo.domain.credit.domain.CreditTransaction;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long>,
        JpaSpecificationExecutor<CreditTransaction> {

    @Query(value = """
            select history.log_id as id,
                   history.amount as amount,
                   history.balance_after as "balanceAfter",
                   history.type as reason,
                   history.created_at as "createdAt"
            from (
                select logs.log_id, logs.amount, logs.type, logs.created_at,
                       users.ball_balance - coalesce(
                           sum(logs.amount) over (
                               order by logs.created_at desc, logs.log_id desc
                               rows between unbounded preceding and 1 preceding
                           ), 0
                       ) as balance_after
                from credit_logs logs
                join users on users.user_id = logs.user_id
                where logs.user_id = :userId
            ) history
            where (:flow is null
                   or (:flow = 'EARN' and history.amount > 0)
                   or (:flow = 'SPEND' and history.amount < 0))
              and (:reason is null or history.type = :reason)
            order by history.created_at desc, history.log_id desc
            """, nativeQuery = true)
    List<CreditTransactionHistory> findHistory(
            @Param("userId") Long userId,
            @Param("flow") String flow,
            @Param("reason") String reason,
            Pageable pageable
    );

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
