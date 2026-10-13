package com.example.campushelp.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private LocalDateTime expectedTime;
    private BigDecimal reward;
    private String location;
    private Long publisherId;
    private String publisherName;
    private Long acceptorId;
    private String acceptorName;
    private String status;
    private String completionProof;
    private String cancelReason;
    private String canceledBy;
    private LocalDateTime canceledAt;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
