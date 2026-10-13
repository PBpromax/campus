package com.example.campushelp.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReviewRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化原生的 Validator 校验器，无需启动 Spring 容器，运行速度极快
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("正向测试：当所有参数在合法范围内时，校验应该通过")
    void testValidReviewRequest_ShouldPass() {
        // 1. 测试合法数据（包含边界值 5）
        ReviewRequest request1 = new ReviewRequest();
        request1.setDemandId(100L);
        request1.setTargetUserId(200L);
        request1.setRating(5);
        request1.setComment("非常准时，体验很好！");

        Set<ConstraintViolation<ReviewRequest>> violations1 = validator.validate(request1);
        assertTrue(violations1.isEmpty(), "合法的评价请求不应该触发校验错误");

        // 2. 测试合法数据（边界值 1，且不带评论）
        ReviewRequest request2 = new ReviewRequest();
        request2.setDemandId(101L);
        request2.setTargetUserId(201L);
        request2.setRating(1);
        request2.setComment(null); // comment 允许为 null

        Set<ConstraintViolation<ReviewRequest>> violations2 = validator.validate(request2);
        assertTrue(violations2.isEmpty(), "没有评论、评分为1也是合法的");
    }

    @Test
    @DisplayName("反向测试：当 demandId 或 targetUserId 为 null 时，校验应该失败")
    void testNullIds_ShouldFail() {
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(null);       // 触发 @NotNull
        request.setTargetUserId(null);   // 触发 @NotNull
        request.setRating(4);

        Set<ConstraintViolation<ReviewRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "ID 为 null 时校验应该失败");
        assertEquals(2, violations.size(), "应该有两个由于 @NotNull 引起的校验错误");

        boolean hasDemandIdError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("demandId"));
        boolean hasTargetUserIdError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("targetUserId"));

        assertTrue(hasDemandIdError);
        assertTrue(hasTargetUserIdError);
    }

    @Test
    @DisplayName("边界值测试：当 rating 小于 1 时，校验应该失败")
    void testRatingTooLow_ShouldFail() {
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(200L);
        request.setRating(0); // 触发 @Min(1)

        Set<ConstraintViolation<ReviewRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "rating 小于 1 时校验应该失败");
        boolean hasMinError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
        assertTrue(hasMinError, "错误应该定位在 rating 属性上");
    }

    @Test
    @DisplayName("边界值测试：当 rating 大于 5 时，校验应该失败")
    void testRatingTooHigh_ShouldFail() {
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(200L);
        request.setRating(6); // 触发 @Max(5)

        Set<ConstraintViolation<ReviewRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "rating 大于 5 时校验应该失败");
        boolean hasMaxError = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
        assertTrue(hasMaxError, "错误应该定位在 rating 属性上");
    }
}