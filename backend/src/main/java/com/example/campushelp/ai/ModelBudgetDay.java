package com.example.campushelp.ai;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="model_budget_days") @Getter @Setter @NoArgsConstructor
public class ModelBudgetDay {
    @Id @Column(length=64) private String id;
    @Column(nullable=false) private long attempts;
    @Column(nullable=false) private long reservedTokens;
    @Column(nullable=false,precision=20,scale=8) private BigDecimal reservedUsd=BigDecimal.ZERO;
}
