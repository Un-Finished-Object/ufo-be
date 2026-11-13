package com.ufo.ufo.domain.pattern.dao;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PatternRecommendationRepository {

    private static final String INTEREST_CONDITION = """
            from Pattern p
            where p.deletedAt is null
              and exists (
                  select 1 from Pattern matching
                  join matching.interestNumbers interestNumber
                  where matching = p and interestNumber in :interestNumbers
              )
            """;
    private static final String FIT_CONDITION = """
              and exists (
                  select 1 from Pattern fitting
                  join fitting.interestNumbers fitNumber
                  where fitting = p and fitNumber in :fitNumbers
              )
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Pattern> findPopular(int limit) {
        return entityManager.createQuery("""
                        select p from Pattern p
                        where p.deletedAt is null
                        order by p.scrapsCount desc, p.id desc
                        """, Pattern.class)
                .setMaxResults(limit)
                .getResultList();
    }

    public long countMatching(List<Integer> interestNumbers, List<Integer> fitNumbers) {
        return matchingQuery("select count(p) ", "", Long.class, interestNumbers, fitNumbers).getSingleResult();
    }

    public List<Pattern> findMatching(List<Integer> interestNumbers, List<Integer> fitNumbers, int offset, int limit) {
        return matchingQuery("select p ", " order by p.id asc", Pattern.class, interestNumbers, fitNumbers)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    private <T> TypedQuery<T> matchingQuery(String select, String order, Class<T> resultType,
                                          List<Integer> interestNumbers, List<Integer> fitNumbers) {
        String condition = INTEREST_CONDITION + (fitNumbers.isEmpty() ? "" : FIT_CONDITION);
        TypedQuery<T> query = entityManager.createQuery(select + condition + order, resultType)
                .setParameter("interestNumbers", interestNumbers);
        if (!fitNumbers.isEmpty()) {
            query.setParameter("fitNumbers", fitNumbers);
        }
        return query;
    }
}
