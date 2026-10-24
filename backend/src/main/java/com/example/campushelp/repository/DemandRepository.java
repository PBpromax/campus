package com.example.campushelp.repository;

import com.example.campushelp.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DemandRepository extends JpaRepository<Demand, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Demand e where e.id=:id")
    java.util.Optional<Demand> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    List<Demand> findByStatus(String status);
    List<Demand> findByPublisherId(Long publisherId);
    List<Demand> findByAcceptorId(Long acceptorId);
    List<Demand> findByCategory(String category);
    List<Demand> findByStatusOrderByCreatedAtDesc(String status);
    List<Demand> findAllByOrderByCreatedAtDesc();

    @Query("SELECT d FROM Demand d WHERE " +
           "(:category IS NULL OR d.category = :category) AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:keyword IS NULL OR d.title LIKE %:keyword% OR d.description LIKE %:keyword% OR d.location LIKE %:keyword%) " +
           "ORDER BY d.createdAt DESC")
    List<Demand> searchDemands(@Param("category") String category,
                               @Param("status") String status,
                               @Param("keyword") String keyword);
}
