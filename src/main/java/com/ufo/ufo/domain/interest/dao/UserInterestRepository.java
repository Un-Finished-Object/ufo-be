package com.ufo.ufo.domain.interest.dao;

import com.ufo.ufo.domain.interest.domain.UserInterest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    List<UserInterest> findAllByUser_Id(Long userId);

    void deleteAllByUser_Id(Long userId);
}
