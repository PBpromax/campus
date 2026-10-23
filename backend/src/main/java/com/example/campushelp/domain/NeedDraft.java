package com.example.campushelp.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="need_drafts", uniqueConstraints=@UniqueConstraint(name="uq_need_session_category", columnNames={"session_id","category"}))
@Getter @Setter @NoArgsConstructor
public class NeedDraft {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="session_id", nullable=false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private NeedCategory category;
    @Column(nullable=false) private long revision = 1;
    private Long confirmedRevision;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String fieldsJson = "{}";
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String contextsJson = "[]";
}
