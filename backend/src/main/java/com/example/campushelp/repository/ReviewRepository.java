package com.example.campushelp.repository;

import com.example.campushelp.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByDemandId(Long demandId);
    List<Review> findByTargetUserId(Long targetUserId);
    Optional<Review> findByDemandIdAndReviewerId(Long demandId, Long reviewerId);
    boolean existsByDemandIdAndReviewerId(Long demandId, Long reviewerId);
}
