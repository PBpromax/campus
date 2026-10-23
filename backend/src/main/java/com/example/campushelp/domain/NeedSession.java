package com.example.campushelp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="need_sessions", indexes=@Index(name="idx_need_owner", columnList="owner_id"))
@Getter @Setter @NoArgsConstructor
public class NeedSession {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id", nullable=false) private Long ownerId;
    @Column(nullable=false) private long revision;
    @Enumerated(EnumType.STRING) @Column(length=16) private NeedCategory activeCategory;
    @Column(nullable=false) private boolean routePending = true;
    @Lob @Column(columnDefinition="LONGTEXT") private String intakeText = "";
    @Column(nullable=false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long rowVersion;
}
