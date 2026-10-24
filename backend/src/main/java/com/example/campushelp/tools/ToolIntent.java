package com.example.campushelp.tools;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="tool_intents",uniqueConstraints={
    @UniqueConstraint(name="uq_tool_actor_request",columnNames={"actor_id","request_key"}),
    @UniqueConstraint(name="uq_tool_publication",columnNames="publication_key")})
@Getter @Setter @NoArgsConstructor
public class ToolIntent {
    @Id @Column(length=36) private String id;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(name="request_key",nullable=false,length=64) private String requestKey;
    @Column(nullable=false,length=64) private String requestHash;
    @Column(nullable=false,length=16) private String action;
    @Column(nullable=false,length=16) private String state="PREPARED";
    private Long sourceSessionId;
    private Long sourceRevision;
    private Long draftRevision;
    private Long demandId;
    private Long demandVersion;
    @Column(name="publication_key",length=100) private String publicationKey;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String payloadJson;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String previewJson;
    @Lob @Column(columnDefinition="LONGTEXT") private String resultJson;
    private Integer errorCode;
    @Column(length=500) private String errorMessage;
    @Column(nullable=false) private LocalDateTime expiresAt;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
