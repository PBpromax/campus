package com.example.campushelp.tools;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ToolIntentRepository extends JpaRepository<ToolIntent,String> {
    Optional<ToolIntent> findByActorIdAndRequestKey(Long actor,String requestKey);
    List<ToolIntent> findByActorIdOrderByCreatedAtDesc(Long actor,Pageable page);
    boolean existsByPublicationKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from ToolIntent t where t.id=:id and t.actorId=:actor")
    Optional<ToolIntent> lockOwned(@Param("id") String id,@Param("actor") Long actor);
}
