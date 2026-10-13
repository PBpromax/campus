package com.example.campushelp.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String studentId;
    private String phone;
    private String name;
    private String college;
    private String major;
    private int creditScore;
    private String role;
    private java.time.LocalDateTime restrictedUntil;
}
