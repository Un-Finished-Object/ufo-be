package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatternRepository extends JpaRepository<Pattern, Long> {

    @Query("""
            select p from Pattern p
            where p.deletedAt is null
              and (:categoryMain is null or p.categoryMain = :categoryMain)
              and (:categorySub is null or p.categorySub = :categorySub)
            """)
    Page<Pattern> findAllByCategory(
            @Param("categoryMain") String categoryMain,
            @Param("categorySub") String categorySub,
            Pageable pageable
    );

    @Query("""
            select p from Pattern p
            where p.deletedAt is null
              and (:categoryMain is null or p.categoryMain = :categoryMain)
              and (:categorySub is null or p.categorySub = :categorySub)
            order by p.scrapsCount desc, p.id desc
            """)
    Page<Pattern> findAllByCategoryOrderByPopularity(
            @Param("categoryMain") String categoryMain,
            @Param("categorySub") String categorySub,
            Pageable pageable
    );

    @Query("""
            select p from Pattern p
            where p.deletedAt is null
              and (lower(p.title) like lower(concat('%', :q, '%'))
                or lower(p.designer) like lower(concat('%', :q, '%')))
            """)
    Page<Pattern> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select p from Pattern p
            where p.deletedAt is null
            order by p.scrapsCount desc, p.id desc
            """)
    List<Pattern> findRecommended();

    @Query("""
            select p from Pattern p
            where p.deletedAt is null
              and exists (
                  select 1 from UserInterest ui
                  where ui.user.id = :userId
                    and (
                        lower(p.title) like lower(concat('%', ui.keyword, '%'))
                        or lower(coalesce(p.categoryMain, '')) like lower(concat('%', ui.keyword, '%'))
                        or lower(coalesce(p.categorySub, '')) like lower(concat('%', ui.keyword, '%'))
                    )
              )
            order by p.scrapsCount desc, p.id desc
            """)
    List<Pattern> findRecommendedByUserInterest(@Param("userId") Long userId);
}
