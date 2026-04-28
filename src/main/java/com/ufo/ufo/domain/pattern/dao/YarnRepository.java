package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YarnRepository extends JpaRepository<Yarn, Long> {
    Optional<Yarn> findFirstByNameIgnoreCaseAndDeletedAtIsNullOrderByYarnIdAsc(String name);
}
