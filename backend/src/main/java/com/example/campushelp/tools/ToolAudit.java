package com.example.campushelp.tools;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="tool_audits",indexes=@Index(name="idx_tool_audit_actor",columnList="actor_id"))
@Getter @Setter @NoArgsConstructor
public class ToolAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(length=36) private String intentId;
    @Column(nullable=false,length=16) private String action;
    @Column(nullable=false,length=20) private String outcome;
    @Column(nullable=false,length=500) private String message;
    @Column(length=36) private String traceId;
    @Column(length=36) private String requestId;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
