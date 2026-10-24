package com.example.campushelp.tools;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface ToolAuditRepository extends JpaRepository<ToolAudit,Long> {
    List<ToolAudit> findByActorIdOrderByCreatedAtDesc(Long actor,Pageable page);
}
