package com.example.campushelp.controller;

import com.example.campushelp.dto.request.AppealHandleRequest;
import com.example.campushelp.dto.request.AppealRequest;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.dto.response.AppealResponse;
import com.example.campushelp.service.AppealService;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appeals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AppealController {

    private final AppealService appealService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppealResponse>> createAppeal(@Valid @RequestBody AppealRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        AppealResponse appeal = appealService.createAppeal(userId, request);
        return ResponseEntity.ok(ApiResponse.success("申诉已提交", appeal));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<AppealResponse>>> getMyAppeals() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AppealResponse> appeals = appealService.getMyAppeals(userId);
        return ResponseEntity.ok(ApiResponse.success(appeals));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppealResponse>>> getAllAppeals() {
        List<AppealResponse> appeals = appealService.getAllAppeals();
        return ResponseEntity.ok(ApiResponse.success(appeals));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<AppealResponse>>> getPendingAppeals() {
        List<AppealResponse> appeals = appealService.getPendingAppeals();
        return ResponseEntity.ok(ApiResponse.success(appeals));
    }

    @PutMapping("/{id}/handle")
    public ResponseEntity<ApiResponse<AppealResponse>> handleAppeal(
            @PathVariable Long id,
            @RequestBody AppealHandleRequest request) {
        Long adminId = SecurityUtils.getCurrentUserId();
        AppealResponse appeal = appealService.handleAppeal(id, adminId, request.getAction(), request.getAdminNote());
        return ResponseEntity.ok(ApiResponse.success("处理完成", appeal));
    }
}
