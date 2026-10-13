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
public class ReviewResponse {
    private Long id;
    private Long demandId;
    private Long reviewerId;
    private String reviewerName;
    private Long targetUserId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
}
