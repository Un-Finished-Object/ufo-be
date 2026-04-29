package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YarnRepository extends JpaRepository<Yarn, Long> {
    @Query("""
            select y
            from Yarn y
            where lower(y.name) = lower(:name)
            and y.deletedAt is null
            and y.thicknessCategory is not null
            and trim(y.thicknessCategory) <> ''
            order by y.yarnId asc
            """)
    List<Yarn> findCategorizedByNameOrderByYarnIdAsc(@Param("name") String name);
}
