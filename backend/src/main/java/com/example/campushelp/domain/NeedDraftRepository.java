package com.example.campushelp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NeedDraftRepository extends JpaRepository<NeedDraft,Long> {
    List<NeedDraft> findBySessionIdOrderById(Long sessionId);
    Optional<NeedDraft> findBySessionIdAndCategory(Long sessionId,NeedCategory category);
    void deleteBySessionId(Long sessionId);
}
