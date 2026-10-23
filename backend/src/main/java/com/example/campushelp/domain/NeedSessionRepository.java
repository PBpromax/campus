package com.example.campushelp.domain;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface NeedSessionRepository extends JpaRepository<NeedSession,Long> {
    List<NeedSession> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NeedSession s where s.id=:id and s.ownerId=:owner")
    Optional<NeedSession> lockOwned(@Param("id") Long id,@Param("owner") Long owner);
}
