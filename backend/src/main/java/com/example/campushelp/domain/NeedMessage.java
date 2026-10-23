package com.example.campushelp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="need_messages",indexes=@Index(name="idx_need_message_session",columnList="session_id"))
@Getter @Setter @NoArgsConstructor
public class NeedMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="session_id",nullable=false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(length=16) private NeedCategory category;
    @Column(nullable=false,length=4000) private String text;
    @Column(nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
}
