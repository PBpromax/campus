package com.example.campushelp.ai;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;

public interface ModelInvocationRepository extends JpaRepository<ModelInvocation,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from ModelInvocation i where i.id=:id")
    Optional<ModelInvocation> lock(@Param("id") String id);
    List<ModelInvocation> findTop20ByOwnerIdAndBudgetDayOrderByCreatedAtDesc(Long owner,LocalDate day);
}
