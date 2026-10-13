package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.ReviewRequest;
import com.example.campushelp.dto.response.ReviewResponse;
import com.example.campushelp.entity.Demand;
import com.example.campushelp.entity.Review;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import com.example.campushelp.repository.ReviewRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.NotificationService;
import com.example.campushelp.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final DemandRepository demandRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewResponse createReview(Long reviewerId, ReviewRequest request) {
        Demand demand = demandRepository.findById(request.getDemandId())
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if (!"COMPLETED".equals(demand.getStatus())) {
            throw new BusinessException("只能评价已完成的需求");
        }

        boolean isPublisher = reviewerId.equals(demand.getPublisherId());
        boolean isAcceptor = reviewerId.equals(demand.getAcceptorId());

        if (!isPublisher && !isAcceptor) {
            throw new BusinessException("只有订单参与方才能评价");
        }

        Long expectedTarget = isPublisher ? demand.getAcceptorId() : demand.getPublisherId();
        if (!expectedTarget.equals(request.getTargetUserId())) {
            throw new BusinessException("评价对象不正确");
        }

        if (reviewRepository.existsByDemandIdAndReviewerId(request.getDemandId(), reviewerId)) {
            throw new BusinessException("您已经评价过此订单");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new BusinessException("目标用户不存在"));

        Review review = new Review();
        review.setDemandId(request.getDemandId());
        review.setReviewerId(reviewerId);
        review.setReviewerName(reviewer.getName());
        review.setTargetUserId(request.getTargetUserId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review savedReview = reviewRepository.save(review);

        // 根据评分更新信用分: 4-5星 +1, 3星 不变, 1-2星 -3
        int creditDelta = 0;
        if (request.getRating() >= 4) {
            creditDelta = 1;
        } else if (request.getRating() <= 2) {
            creditDelta = -3;
        }

        if (creditDelta != 0) {
            updateCreditScore(targetUser, creditDelta);
            String deltaStr = creditDelta > 0 ? "+" + creditDelta : String.valueOf(creditDelta);
            notificationService.createNotification(
                    targetUser.getId(),
                    "信用分变动",
                    "您在订单「" + demand.getTitle() + "」中收到的评价使信用分" + deltaStr + "，当前信用分：" + targetUser.getCreditScore(),
                    "CREDIT_CHANGE",
                    savedReview.getId()
            );
        }

        // 通知对方收到了评价
        notificationService.createNotification(
                targetUser.getId(),
                "收到新评价",
                reviewer.getName() + "对您进行了" + request.getRating() + "星评价",
                "REVIEW_RECEIVED",
                savedReview.getId()
        );

        return convertToResponse(savedReview);
    }

    @Override
    public List<ReviewResponse> getReviewsByDemand(Long demandId) {
        return reviewRepository.findByDemandId(demandId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewResponse> getReviewsByUser(Long userId) {
        return reviewRepository.findByTargetUserId(userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private void updateCreditScore(User user, int delta) {
        int newScore = Math.max(0, user.getCreditScore() + delta);
        user.setCreditScore(newScore);

        if (newScore < 40) {
            user.setRestrictedUntil(LocalDateTime.of(2099, 12, 31, 23, 59));
        } else if (newScore < 60 && (user.getRestrictedUntil() == null || user.getRestrictedUntil().isBefore(LocalDateTime.now()))) {
            user.setRestrictedUntil(LocalDateTime.now().plusDays(3));
        } else if (newScore >= 60 && user.getRestrictedUntil() != null) {
            user.setRestrictedUntil(null);
        }

        userRepository.save(user);
    }

    private ReviewResponse convertToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .demandId(review.getDemandId())
                .reviewerId(review.getReviewerId())
                .reviewerName(review.getReviewerName())
                .targetUserId(review.getTargetUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
