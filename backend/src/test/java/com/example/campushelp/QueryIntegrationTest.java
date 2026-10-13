package com.example.campushelp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.campushelp.entity.Review;
import com.example.campushelp.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4.3 集成测试要求三：前后端联调中的关键接口调用测试
 * 重点针对前端各类“详情页”和“个人中心”拉取评价列表的关键数据联调接口进行断言
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 开启事务自动回滚，确保测试数据不污染数据库
public class QueryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    private Long testDemandId;
    private Long testTargetUserId;

    /**
     * 初始化基础联调数据：在数据库中预先打入两条评价实体，供前端拉取接口调用
     */
    @BeforeEach
    public void setUp() {
        Random random = new Random();
        testDemandId = (long) (random.nextInt(90000) + 10000);
        testTargetUserId = (long) (random.nextInt(90000) + 10000);

        // 模拟创建第一条评价数据
        Review review1 = new Review();
        review1.setDemandId(testDemandId);
        review1.setReviewerId(111L);
        review1.setReviewerName("张三同学");
        review1.setTargetUserId(testTargetUserId);
        review1.setRating(5);
        review1.setComment("联调测试：非常准时！");
        review1.setCreatedAt(LocalDateTime.now());

        // 模拟创建第二条评价数据
        Review review2 = new Review();
        review2.setDemandId(testDemandId);
        review2.setReviewerId(222L);
        review2.setReviewerName("李四同学");
        review2.setTargetUserId(testTargetUserId);
        review2.setRating(4);
        review2.setComment("联调测试：包装完整。");
        review2.setCreatedAt(LocalDateTime.now());

        // 持久化到数据层
        reviewRepository.save(review1);
        reviewRepository.save(review2);
    }

    /**
     * 🔍 关键接口联调一：根据需求单 ID 查询评价列表
     * 模拟场景：前端用户进入“订单详情页”时，拉取该订单下的所有历史评价
     */
    @Test
    public void testGetReviewsByDemand_JointDebugging() throws Exception {
        // 执行 GET 请求调用关键接口
        mockMvc.perform(get("/api/reviews/demand/" + testDemandId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // 断言业务状态码为 200
                .andExpect(jsonPath("$.code").value(200))
                // 断言返回的数据为数组结构
                .andExpect(jsonPath("$.data").isArray())
                // 断言该单据下能成功关联并查询出 2 条数据
                .andExpect(jsonPath("$.data.length()").value(2))
                // 精准断言返回的字段结构符合 ReviewResponse 的 DTO 规范
                .andExpect(jsonPath("$.data[0].demandId").value(testDemandId))
                .andExpect(jsonPath("$.data[0].reviewerName").value("张三同学"))
                .andExpect(jsonPath("$.data[1].reviewerName").value("李四同学"));
    }

    /**
     * 🔍 关键接口联调二：根据目标用户 ID 查询收到的所有评价列表
     * 模拟场景：前端用户进入“个人中心 -> 我的评价”或者查看“接单人主页信用评价”时的数据展示
     */
    @Test
    public void testGetReviewsByUser_JointDebugging() throws Exception {
        // 执行 GET 请求调用关键接口
        mockMvc.perform(get("/api/reviews/user/" + testTargetUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // 断言返回结构与前后端联调约定的规范完全匹配
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].targetUserId").value(testTargetUserId))
                .andExpect(jsonPath("$.data[0].comment").value("联调测试：非常准时！"))
                .andExpect(jsonPath("$.data[1].comment").value("联调测试：包装完整。"));
    }
}