package com.example.campushelp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageRequest {
    @NotNull
    private Long demandId;

    @NotNull
    private Long receiverId;

    @NotBlank
    private String content;
}
