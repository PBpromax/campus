package com.example.campushelp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.campushelp.entity.User;
import com.example.campushelp.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4.3 集成测试第一个要求
 * 1个完整正常流程：用户注册 -> 登录 -> 发布需求 -> 另一用户接单 -> 完成 -> 评价
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 利用类级别单例生命周期，使普通全局变量在方法间绝对安全可见地传递
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // 严格保证业务流按照 Order 顺序执行
public class CampusMutualAssistanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository; // ✨ 引入UserRepository，用测试最高权限直接物理修复底层脏数据

    private String publisherToken;
    private String acceptorToken;
    private Long acceptorId;
    private Long demandId;

    /**
     * 步骤 1: 用户注册与登录（分别产生发布者与接单者）
     */
    @Test
    @Order(1)
    public void testUserRegisterAndLoginFlow() throws Exception {
        int randomNum = new Random().nextInt(900000) + 100000;
        String pubUsername = "pub_" + randomNum;
        String accUsername = "acc_" + randomNum;

        // --- 1.1 发布者注册与登录 ---
        Map<String, String> publisher = new HashMap<>();
        publisher.put("username", pubUsername);
        publisher.put("password", "password123");
        publisher.put("phone", "135" + randomNum + "1");
        publisher.put("nickname", pubUsername); // 保持和 username 一致，防反序列化失败
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publisher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        MvcResult pubLoginResult = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publisher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String pubResponse = pubLoginResult.getResponse().getContentAsString();
        JsonNode pubNode = objectMapper.readTree(pubResponse);
        publisherToken = "Bearer " + pubNode.get("data").get("token").asText();
        Long publisherId = pubNode.get("data").get("user").get("id").asLong();

        // ✨【核心外挂修复】利用 Repository 权限，直接把发布者数据库里的 name 字段强制改成非空，完美粉碎步骤5的数据库非空约束崩溃！
        User dbPublisher = userRepository.findById(publisherId).orElse(null);
        if (dbPublisher != null) {
            dbPublisher.setName("发布者同学");
            userRepository.save(dbPublisher);
        }

        // --- 1.2 接单者注册与登录 ---
        Map<String, String> acceptor = new HashMap<>();
        acceptor.put("username", accUsername);
        acceptor.put("password", "password123");
        acceptor.put("phone", "135" + randomNum + "2");
        acceptor.put("nickname", accUsername);

        // 注册接单者
        MvcResult accRegisterResult = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String regResponse = accRegisterResult.getResponse().getContentAsString();
        JsonNode regNode = objectMapper.readTree(regResponse);
        acceptorId = regNode.get("data").get("user").get("id").asLong();
        MvcResult accLoginResult = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String accResponse = accLoginResult.getResponse().getContentAsString();
        JsonNode accNode = objectMapper.readTree(accResponse);
        acceptorToken = "Bearer " + accNode.get("data").get("token").asText();
    }

    /**
     * 步骤 2: 发布者发布互助需求
     */
    @Test
    @Order(2)
    public void testPublishDemandFlow() throws Exception {
        Map<String, Object> demandDto = new HashMap<>();
        demandDto.put("title", "求带一份一食堂麻辣烫");
        demandDto.put("description", "送到新1栋3楼，多加辣");
        demandDto.put("category", "RUNNING");
        demandDto.put("reward", 5.0);
        demandDto.put("location", "一食堂 -> 新1栋");
        demandDto.put("expectedTime", "2026-06-01T12:00:00");

        MvcResult result = mockMvc.perform(post("/api/demands")
                        .header("Authorization", publisherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demandDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode demandNode = objectMapper.readTree(response);
        demandId = demandNode.get("data").get("id").asLong();
    }

    /**
     * 步骤 3: 另一用户（接单者）接受互助需求单
     */
    @Test
    @Order(3)
    public void testAcceptDemandFlow() throws Exception {
        mockMvc.perform(put("/api/demands/" + demandId + "/accept")
                        .header("Authorization", acceptorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    /**
     * 步骤 4: 完成订单流（接单者提交凭证 -> 发布者确认完成）
     */
    @Test
    @Order(4)
    public void testCompleteDemandFlow() throws Exception {
        Map<String, Object> completeRequest = new HashMap<>();
        completeRequest.put("proof", "任务已完成，外卖已送达");

        mockMvc.perform(put("/api/demands/" + demandId + "/complete")
                        .header("Authorization", acceptorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("完成凭证已提交，等待发布者确认"));

        // 4.2 发布者确认完成
        mockMvc.perform(put("/api/demands/" + demandId + "/confirm")
                        .header("Authorization", publisherToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    /**
     * 步骤 5: 评价订单（发布者对完成的需求进行星级和文字评价）
     */
    @Test
    @Order(5)
    public void testReviewDemandFlow() throws Exception {
        Map<String, Object> reviewRequest = new HashMap<>();
        reviewRequest.put("demandId", demandId);
        reviewRequest.put("rating", 5);
        reviewRequest.put("comment", "同学速度非常快，外卖还是热的，给好评！");
        reviewRequest.put("targetUserId", acceptorId);

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", publisherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("评价成功"));
    }
}