
package com.example.campushelp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long rowVersion;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, length = 20)
    private String studentId;

    @Column(unique = true, length = 11)
    private String phone;
    
    @Column(nullable = false, length = 100)
    private String password;
    
    @Column(length = 50)
    private String name;
    
    @Column(length = 100)
    private String college;
    
    @Column(length = 100)
    private String major;

    @Column(nullable = false)
    private int creditScore = 100;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    private LocalDateTime restrictedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
