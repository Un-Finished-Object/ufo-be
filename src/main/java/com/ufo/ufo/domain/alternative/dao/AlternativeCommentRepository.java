package com.ufo.ufo.domain.alternative.dao;

import com.ufo.ufo.domain.alternative.domain.AlternativeComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlternativeCommentRepository extends JpaRepository<AlternativeComment, Long> {

    Page<AlternativeComment> findAllByAlternative_Id(Long altId, Pageable pageable);
}
