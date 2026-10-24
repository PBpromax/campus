package com.example.campushelp.ai;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="model_invocations",indexes=@Index(name="idx_model_owner_day",columnList="owner_id,budget_day")) @Getter @Setter @NoArgsConstructor
public class ModelInvocation {
    @Id @Column(length=36) private String id;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="budget_day",nullable=false) private LocalDate budgetDay;
    @Column(length=36) private String traceId;
    @Column(length=36) private String requestId;
    @Column(nullable=false,length=64) private String model;
    @Column(nullable=false,length=64) private String priceVersion;
    @Column(nullable=false,length=20) private String state="RESERVED";
    @Column(length=40) private String failureKind;
    @Column(nullable=false) private long reservedTokens;
    @Column(nullable=false,precision=20,scale=8) private BigDecimal reservedUsd;
    private Long promptTokens;
    private Long completionTokens;
    @Column(precision=20,scale=8) private BigDecimal estimatedUsd;
    @Column(length=40) private String estimateBasis;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    private Instant finishedAt;
}
