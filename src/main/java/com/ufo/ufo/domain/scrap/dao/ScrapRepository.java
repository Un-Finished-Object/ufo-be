package com.ufo.ufo.domain.scrap.dao;

import com.ufo.ufo.domain.scrap.domain.Scrap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUser_IdAndPattern_Id(Long userId, Long patternId);

    @Query("""
            select s.pattern.id from Scrap s
            where s.user.id = :userId and s.pattern.id in :patternIds
            """)
    Set<Long> findScrappedPatternIds(@Param("userId") Long userId, @Param("patternIds") List<Long> patternIds);

    Optional<Scrap> findByUser_IdAndPattern_Id(Long userId, Long patternId);

    @EntityGraph(attributePaths = "pattern")
    Page<Scrap> findAllByUser_IdAndPattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
