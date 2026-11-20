package com.ufo.ufo.domain.user.dao;

import com.ufo.ufo.domain.user.dao.projection.PurchasedProjectSummary;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserProjectRepository extends Repository<User, Long> {

    @Query(value = """
            select count(*)
            from patterns pattern
            join (
                select unlocks.pattern_id
                from unlocks
                where unlocks.user_id = :userId and unlocks.type = 'YARN_INFO'
                union
                select room.pattern_id
                from chat_room_statuses status
                join chat_rooms room on room.chat_room_id = status.chat_room_id
                where status.user_id = :userId
            ) purchased on purchased.pattern_id = pattern.pattern_id
            where pattern.deleted_at is null
            """, nativeQuery = true)
    long countPurchasedProjects(@Param("userId") Long userId);

    @Query(value = """
            with purchases as (
                select unlocks.pattern_id,
                       unlocks.unlock_id as yarn_purchase_id,
                       unlocks.created_at as yarn_purchase_date,
                       null as chat_room_id,
                       null as chat_purchase_date,
                       0 as chat_rank
                from unlocks
                where unlocks.user_id = :userId and unlocks.type = 'YARN_INFO'
                union all
                select room.pattern_id,
                       null as yarn_purchase_id,
                       null as yarn_purchase_date,
                       status.chat_room_id,
                       status.created_at as chat_purchase_date,
                       row_number() over (
                           partition by room.pattern_id
                           order by case when status.created_at is null then 1 else 0 end,
                                    status.created_at desc, status.chat_room_status_id desc
                       ) as chat_rank
                from chat_room_statuses status
                join chat_rooms room on room.chat_room_id = status.chat_room_id
                where status.user_id = :userId
            ), projects as (
                select pattern_id,
                       max(yarn_purchase_id) as yarn_purchase_id,
                       max(yarn_purchase_date) as yarn_purchase_date,
                       max(case when chat_rank = 1 then chat_room_id end) as chat_room_id,
                       max(chat_purchase_date) as chat_purchase_date,
                       max(coalesce(yarn_purchase_date, chat_purchase_date)) as latest_purchase_date
                from purchases
                group by pattern_id
            )
            select pattern.pattern_id as "patternId",
                   pattern.title as "patternName",
                   pattern.designer as author,
                   pattern.thumbnail_url as "thumbnailKey",
                   projects.yarn_purchase_id as "yarnPurchaseId",
                   projects.yarn_purchase_date as "purchaseYarnDate",
                   projects.chat_room_id as "purchaseChatId",
                   projects.chat_purchase_date as "purchaseChatDate"
            from projects
            join patterns pattern on pattern.pattern_id = projects.pattern_id
            where pattern.deleted_at is null
            order by case when projects.latest_purchase_date is null then 1 else 0 end,
                     projects.latest_purchase_date desc, pattern.pattern_id desc
            limit :pageSize offset :offset
            """, nativeQuery = true)
    List<PurchasedProjectSummary> findPurchasedProjects(
            @Param("userId") Long userId,
            @Param("pageSize") int pageSize,
            @Param("offset") long offset
    );
}
