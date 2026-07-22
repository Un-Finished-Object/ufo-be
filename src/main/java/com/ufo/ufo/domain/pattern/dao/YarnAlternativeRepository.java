package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YarnAlternativeRepository extends JpaRepository<YarnAlternative, Long> {

    @Query("""
            select alternative
            from YarnAlternative alternative
            join fetch alternative.alternativeYarn
            where alternative.originalYarn.yarnId = :originalYarnId
            order by alternative.ranking asc, alternative.id asc
            """)
    List<YarnAlternative> findAllByOriginalYarnId(@Param("originalYarnId") Long originalYarnId);
}
