package com.example.campushelp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NeedMessageRepository extends JpaRepository<NeedMessage,Long> {
    List<NeedMessage> findBySessionIdOrderById(Long sessionId);
    long countBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
