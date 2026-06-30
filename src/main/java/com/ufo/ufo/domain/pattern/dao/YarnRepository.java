package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YarnRepository extends JpaRepository<Yarn, Long> {

    Optional<Yarn> findByYarnIdAndDeletedAtIsNull(Long yarnId);

    @Query("""
            select distinct y
            from Yarn y
            left join fetch y.gauges
            where y.deletedAt is null
            and y.yarnId <> :excludedYarnId
            and lower(y.thicknessCategory) = lower(:thicknessCategory)
            and lower(y.mainComponent) = lower(:mainComponent)
            order by y.yarnId asc
            """)
    List<Yarn> findAllActiveByThicknessCategoryAndMainComponentExcludingYarnId(
            @Param("thicknessCategory") String thicknessCategory,
            @Param("mainComponent") String mainComponent,
            @Param("excludedYarnId") Long excludedYarnId
    );
}
