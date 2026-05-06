package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatternRepository extends JpaRepository<Pattern, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pattern> findByIdAndDeletedAtIsNull(Long patternId);

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
            select distinct p from Pattern p
            join p.interestNumbers interestNumber
            where p.deletedAt is null
              and interestNumber in :interestNumbers
            """)
    List<Pattern> findRecommendedByInterestNumbers(@Param("interestNumbers") List<Integer> interestNumbers);
}
