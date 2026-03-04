package com.ufo.ufo.domain.alternative.dao;

import com.ufo.ufo.domain.alternative.domain.AlternativeReaction;
import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlternativeReactionRepository extends JpaRepository<AlternativeReaction, Long> {

    Optional<AlternativeReaction> findByAlternative_IdAndUser_Id(Long altId, Long userId);

    long countByAlternative_IdAndType(Long altId, AlternativeReactionType type);
}
