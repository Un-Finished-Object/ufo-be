package com.ufo.ufo.domain.scrap.dao;

import com.ufo.ufo.domain.scrap.domain.Scrap;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUser_IdAndPattern_Id(Long userId, Long patternId);

    Optional<Scrap> findByUser_IdAndPattern_Id(Long userId, Long patternId);

    @EntityGraph(attributePaths = "pattern")
    Page<Scrap> findAllByUser_IdAndPattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
