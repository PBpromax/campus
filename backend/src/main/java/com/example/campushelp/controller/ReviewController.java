package com.example.campushelp.controller;

import com.example.campushelp.dto.request.ReviewRequest;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.dto.response.ReviewResponse;
import com.example.campushelp.service.ReviewService;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(@Valid @RequestBody ReviewRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ReviewResponse review = reviewService.createReview(userId, request);
        return ResponseEntity.ok(ApiResponse.success("评价成功", review));
    }

    @GetMapping("/demand/{demandId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByDemand(@PathVariable Long demandId) {
        List<ReviewResponse> reviews = reviewService.getReviewsByDemand(demandId);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByUser(@PathVariable Long userId) {
        List<ReviewResponse> reviews = reviewService.getReviewsByUser(userId);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }
}
