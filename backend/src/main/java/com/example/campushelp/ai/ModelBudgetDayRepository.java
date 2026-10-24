package com.example.campushelp.ai;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ModelBudgetDayRepository extends JpaRepository<ModelBudgetDay,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from ModelBudgetDay b where b.id=:id")
    Optional<ModelBudgetDay> lock(@Param("id") String id);
}
