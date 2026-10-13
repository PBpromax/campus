package com.example.campushelp.dto.request;

import lombok.Data;

@Data
public class AppealHandleRequest {
    private String adminNote;
    private String action; // APPROVED or REJECTED
}
