package com.example.campushelp.service;

import com.example.campushelp.dto.request.ReviewRequest;
import com.example.campushelp.dto.response.ReviewResponse;
import java.util.List;

public interface ReviewService {
    ReviewResponse createReview(Long reviewerId, ReviewRequest request);
    List<ReviewResponse> getReviewsByDemand(Long demandId);
    List<ReviewResponse> getReviewsByUser(Long userId);
}
