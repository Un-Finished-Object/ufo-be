package com.ufo.ufo.domain.scrap.dao;

import com.ufo.ufo.domain.scrap.domain.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {
    boolean existsByUser_IdAndPattern_Id(Long userId, Long patternId);
}
