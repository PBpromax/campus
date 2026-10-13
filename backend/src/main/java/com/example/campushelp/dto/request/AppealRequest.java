package com.example.campushelp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppealRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String reason;
}
