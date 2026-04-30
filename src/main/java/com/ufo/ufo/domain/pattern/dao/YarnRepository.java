package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YarnRepository extends JpaRepository<Yarn, Long> {

    @Query("""
            select distinct y
            from Yarn y
            left join fetch y.gauges
            where y.deletedAt is null
            and lower(y.thicknessCategory) = lower(:thicknessCategory)
            order by y.yarnId asc
            """)
    List<Yarn> findAllActiveByThicknessCategory(@Param("thicknessCategory") String thicknessCategory);
}
