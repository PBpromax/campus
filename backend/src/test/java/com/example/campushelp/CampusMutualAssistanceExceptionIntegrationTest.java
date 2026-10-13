package com.example.campushelp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4.3 集成测试第二个要求
 * 编写2个异常业务流程测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // ✨ 关键点：异常测试启用自动事务回滚，确保测试方法之间干净独立、数据不交叉崩溃
public class CampusMutualAssistanceExceptionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private Long userId;

    /**
     * 每个测试方法执行前，先注册并登录一个基础用户，拿到 Token 和 ID
     */
    @BeforeEach
    public void setUp() throws Exception {
        int randomNum = new Random().nextInt(900000) + 100000;
        Map<String, String> user = new HashMap<>();
        user.put("username", "test_err_" + randomNum);
        user.put("password", "password123");
        user.put("phone", "138" + randomNum + "0");
        user.put("nickname", "异常测试专用");

        // 注册
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk());

        // 登录
        MvcResult loginResult = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        userToken = "Bearer " + node.get("data").get("token").asText();
        userId = node.get("data").get("user").get("id").asLong();
    }

    /**
     * ❌ 异常业务流程一：用户试图接受“自己发布的需求”
     * 预期结果：后端拦截阻止，返回非 200 状态码或统一的业务错误码（通常是 400 码或自定义错）
     */
    @Test
    public void testAcceptOwnDemand_ShouldFail() throws Exception {
        // 1. 该用户发布一个需求单
        Map<String, Object> demandDto = new HashMap<>();
        demandDto.put("title", "测试自产自销异常单");
        demandDto.put("description", "不符合接单规则的测试");
        demandDto.put("category", "RUNNING");
        demandDto.put("reward", 10.0);
        demandDto.put("location", "新1栋宿舍楼");
        demandDto.put("expectedTime", "2026-06-01T12:00:00");

        MvcResult result = mockMvc.perform(post("/api/demands")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demandDto)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode demandNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Long demandId = demandNode.get("data").get("id").asLong();

        // 2. 【异常核心行为】：该用户用自己的 Token 试图去强行接受自己发布的单子
        mockMvc.perform(put("/api/demands/" + demandId + "/accept")
                        .header("Authorization", userToken) // 使用同一个人的Token
                        .contentType(MediaType.APPLICATION_JSON))
                // 预期：后端业务抛出异常阻拦。
                // 若系统设计了统一的异常处理包装器，则状态码是 200，但内部业务 code 是非 200（如 400 / 500）
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * ❌ 异常业务流程二：订单处于“发布状态(非COMPLETED)”，用户试图强行对其发表评价
     * 预期结果：触发后端 ReviewServiceImpl 中的限制，报错“只能对已完成的需求进行评价”
     */
    @Test
    public void testReviewUncompletedDemand_ShouldFail() throws Exception {
        // 1. 该用户发布一个初始需求（此时需求状态处于默认的 NEW/PUBLISHED 状态）
        Map<String, Object> demandDto = new HashMap<>();
        demandDto.put("title", "测试未完成直接评价单");
        demandDto.put("description", "刚发出的单子");
        demandDto.put("category", "RUNNING");
        demandDto.put("reward", 5.0);
        demandDto.put("location", "一食堂");
        demandDto.put("expectedTime", "2026-06-01T12:00:00");

        MvcResult result = mockMvc.perform(post("/api/demands")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demandDto)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode demandNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Long demandId = demandNode.get("data").get("id").asLong();

        // 2. 【异常核心行为】：趁着这个单子还没人接、更没有任何人去点击完成，发布者直接强行拼装参数调用评价接口
        Map<String, Object> reviewRequest = new HashMap<>();
        reviewRequest.put("demandId", demandId);
        reviewRequest.put("rating", 5);
        reviewRequest.put("comment", "强行评价测试");
        reviewRequest.put("targetUserId", 9999L); // 虚拟一个目标用户

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                // 预期：触发 ReviewServiceImpl 中的 ! "COMPLETED".equals(demand.getStatus()) 校验限制
                // 统一返回拦截响应，业务 code 应该是业务异常定义的 400 或者是 500
                .andExpect(jsonPath("$.code").value(400));
    }
}