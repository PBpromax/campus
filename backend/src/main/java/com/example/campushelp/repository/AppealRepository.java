package com.example.campushelp.repository;

import com.example.campushelp.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppealRepository extends JpaRepository<Appeal, Long> {
    List<Appeal> findByUserId(Long userId);
    List<Appeal> findByStatusOrderByCreatedAtDesc(String status);
    List<Appeal> findAllByOrderByCreatedAtDesc();
}
