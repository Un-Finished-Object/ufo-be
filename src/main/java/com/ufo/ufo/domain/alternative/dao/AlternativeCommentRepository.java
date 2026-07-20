package com.ufo.ufo.domain.alternative.dao;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlternativeCommentRepository extends JpaRepository<AlternativeComment, Long> {

    @EntityGraph(attributePaths = "user")
    Page<AlternativeComment> findAllByAlternative_IdAndDeletedAtIsNull(Long altId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<AlternativeComment> findByIdAndAlternative_IdAndDeletedAtIsNull(Long commentId, Long altId);
}
