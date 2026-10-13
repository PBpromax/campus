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
public class MessageResponse {
    private Long id;
    private Long demandId;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String content;
    private LocalDateTime createdAt;
}
