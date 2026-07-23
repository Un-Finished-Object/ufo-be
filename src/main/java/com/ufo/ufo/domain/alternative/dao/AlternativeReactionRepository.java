package com.ufo.ufo.domain.alternative.dao;

import com.ufo.ufo.domain.alternative.domain.AlternativeReaction;
import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlternativeReactionRepository extends JpaRepository<AlternativeReaction, Long> {

    Optional<AlternativeReaction> findByYarnAlternative_IdAndUser_Id(Long yarnAlternativeId, Long userId);

    long countByYarnAlternative_IdAndType(Long yarnAlternativeId, AlternativeReactionType type);
}
