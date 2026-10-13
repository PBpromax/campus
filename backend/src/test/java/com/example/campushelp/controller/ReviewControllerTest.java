package com.example.campushelp.controller;

import com.example.campushelp.dto.request.ReviewRequest;
import com.example.campushelp.dto.response.ReviewResponse;
import com.example.campushelp.service.ReviewService;
import com.example.campushelp.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // 放行 Security 安全过滤器，彻底避免单测报 403 错误
public class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    /**
     * 测试创建评价接口
     */
    @Test
    void testCreateReview_Success() throws Exception {
        // 1. 构造请求体 DTO
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(1L);
        request.setRating(5);
        request.setComment("服务非常准时，感谢同学帮忙！");
        request.setTargetUserId(200L); // 👈 【核心修复】：补全后端 @NotNull 强校验的必填字段

        // 2. 构造预期的响应体 DTO
        ReviewResponse response = new ReviewResponse();
        response.setId(10L);
        response.setDemandId(1L);
        response.setRating(5);
        response.setComment("服务非常准时，感谢同学帮忙！");

        // 3. 模拟静态安全上下文：获取当前登录的用户 ID 为 100L
        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格适配源码逻辑：reviewService.createReview(userId, request)
            when(reviewService.createReview(eq(100L), any(ReviewRequest.class))).thenReturn(response);

            // 4. 执行 MockMVC 请求并验证结果
            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()) // 此时参数齐全，不会触发 400，完美通过并返回 200！
                    .andExpect(jsonPath("$.message").value("评价成功"))
                    .andExpect(jsonPath("$.data.id").value(10L))
                    .andExpect(jsonPath("$.data.comment").value("服务非常准时，感谢同学帮忙！"));
        }
    }

    /**
     * 测试根据需求 ID 查询评价接口
     */
    @Test
    void testGetReviewsByDemand() throws Exception {
        List<ReviewResponse> mockList = new ArrayList<>();
        ReviewResponse review = new ReviewResponse();
        review.setId(10L);
        review.setDemandId(1L);
        mockList.add(review);

        when(reviewService.getReviewsByDemand(eq(1L))).thenReturn(mockList);

        mockMvc.perform(get("/api/reviews/demand/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(10L));
    }

    /**
     * 测试根据用户 ID 查询评价接口
     */
    @Test
    void testGetReviewsByUser() throws Exception {
        List<ReviewResponse> mockList = new ArrayList<>();
        ReviewResponse review = new ReviewResponse();
        review.setId(11L);
        review.setDemandId(2L);
        mockList.add(review);

        when(reviewService.getReviewsByUser(eq(100L))).thenReturn(mockList);

        mockMvc.perform(get("/api/reviews/user/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(11L));
    }
}