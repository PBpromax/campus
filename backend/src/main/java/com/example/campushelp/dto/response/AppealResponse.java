package com.example.campushelp.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppealResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String type;
    private String reason;
    private String status;
    private String adminNote;
    private Long handledBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
