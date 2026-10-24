
package com.example.campushelp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "demands")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Demand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long rowVersion;

    @Column(nullable = false, length = 100)
    private String title;
    
    @Column(nullable = false, length = 500)
    private String description;
    
    @Column(nullable = false, length = 50)
    private String category;
    
    @Column(nullable = false)
    private LocalDateTime expectedTime;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal reward;
    
    @Column(nullable = false, length = 200)
    private String location;
    
    @Column(nullable = false)
    private Long publisherId;
    
    @Column(length = 50)
    private String publisherName;
    
    @Column
    private Long acceptorId;
    
    @Column(length = 50)
    private String acceptorName;
    
    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String completionProof;

    @Column(length = 500)
    private String cancelReason;

    @Column(length = 20)
    private String canceledBy;

    private LocalDateTime canceledAt;

    @Column(nullable = false, length = 20)
    private String paymentStatus = "UNPAID";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
