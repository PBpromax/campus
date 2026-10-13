package com.example.campushelp.controller;

import com.example.campushelp.dto.request.MessageRequest;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.dto.response.MessageResponse;
import com.example.campushelp.service.MessageService;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(@Valid @RequestBody MessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        MessageResponse message = messageService.sendMessage(userId, request);
        return ResponseEntity.ok(ApiResponse.success("发送成功", message));
    }

    @GetMapping("/demand/{demandId}")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessagesByDemand(@PathVariable Long demandId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<MessageResponse> messages = messageService.getMessagesByDemand(demandId, userId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMyConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<MessageResponse> messages = messageService.getMyConversations(userId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
}
