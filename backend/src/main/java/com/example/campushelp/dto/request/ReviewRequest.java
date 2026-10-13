package com.example.campushelp.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequest {
    @NotNull
    private Long demandId;

    @NotNull
    private Long targetUserId;

    @Min(1)
    @Max(5)
    private int rating;

    private String comment;
}
