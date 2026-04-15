package com.ufo.ufo.domain.scrap.dao;

import com.ufo.ufo.domain.scrap.domain.Scrap;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUser_IdAndPattern_Id(Long userId, Long patternId);

    Optional<Scrap> findByUser_IdAndPattern_Id(Long userId, Long patternId);

    @Query("""
            select s from Scrap s
            join s.pattern p
            where s.user.id = :userId
              and p.deletedAt is null
            order by s.createdAt desc, s.id desc
            """)
    Page<Scrap> findAllPatternsByUserIdOrderByLatest(@Param("userId") Long userId, Pageable pageable);
}
