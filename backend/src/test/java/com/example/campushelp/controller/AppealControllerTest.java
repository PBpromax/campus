package com.example.campushelp.controller;

import com.example.campushelp.dto.request.AppealHandleRequest;
import com.example.campushelp.dto.request.AppealRequest;
import com.example.campushelp.dto.response.AppealResponse;
import com.example.campushelp.service.AppealService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // 👈 【核心防护】：放行 Security 拦截过滤器，彻底杜绝单测报 403 错误
public class AppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppealService appealService;

    /**
     * 测试创建申诉接口
     */
    /**
     * 测试创建申诉接口
     */
    @Test
    void testCreateAppeal_Success() throws Exception {
        // 1. 构造请求体 DTO
        AppealRequest request = new AppealRequest();
        // 👈 【核心修复】：补全后端 @NotBlank 强校验的必填字段
        request.setReason("接单人超时未完成，且拒绝沟通");
        request.setType("TIMEOUT");

        // 如果您的 AppealRequest 还有其它必填字段（如 demandId），也可以在此行下方按需补全：
        // request.setDemandId(1L);

        // 2. 构造预期返回的响应体 DTO
        AppealResponse response = new AppealResponse();
        response.setId(10L);

        // 3. 模拟静态安全上下文：获取当前登录的用户 ID 为 100L
        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格适配源码：appealService.createAppeal(userId, request)
            when(appealService.createAppeal(eq(100L), any(AppealRequest.class))).thenReturn(response);

            // 4. 执行 MockMvc 请求并验证
            mockMvc.perform(post("/api/appeals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()) // 此时参数齐全，不会触发 400，完美通过并返回 200！
                    .andExpect(jsonPath("$.message").value("申诉已提交"))
                    .andExpect(jsonPath("$.data.id").value(10L));
        }
    }
    /**
     * 测试获取“我的申诉”接口
     */
    @Test
    void testGetMyAppeals() throws Exception {
        List<AppealResponse> mockList = new ArrayList<>();
        AppealResponse appeal = new AppealResponse();
        appeal.setId(10L);
        mockList.add(appeal);

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格适配源码：appealService.getMyAppeals(userId)
            when(appealService.getMyAppeals(100L)).thenReturn(mockList);

            mockMvc.perform(get("/api/appeals/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(10L));
        }
    }

    /**
     * 测试管理员获取“所有申诉”接口
     */
    @Test
    void testGetAllAppeals() throws Exception {
        List<AppealResponse> mockList = new ArrayList<>();
        AppealResponse appeal = new AppealResponse();
        appeal.setId(11L);
        mockList.add(appeal);

        // 严格适配源码：appealService.getAllAppeals()
        when(appealService.getAllAppeals()).thenReturn(mockList);

        mockMvc.perform(get("/api/appeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(11L));
    }

    /**
     * 测试管理员获取“待处理申诉”接口
     */
    @Test
    void testGetPendingAppeals() throws Exception {
        List<AppealResponse> mockList = new ArrayList<>();
        AppealResponse appeal = new AppealResponse();
        appeal.setId(12L);
        mockList.add(appeal);

        // 严格适配源码：appealService.getPendingAppeals()
        when(appealService.getPendingAppeals()).thenReturn(mockList);

        mockMvc.perform(get("/api/appeals/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(12L));
    }

    /**
     * 测试管理员处理申诉接口
     */
    @Test
    void testHandleAppeal_Success() throws Exception {
        // 1. 构造请求体 DTO
        AppealHandleRequest request = new AppealHandleRequest();
        request.setAction("APPROVED");
        request.setAdminNote("审核通过");

        // 2. 构造预期返回的响应体 DTO
        AppealResponse response = new AppealResponse();
        response.setId(1L);

        // 3. 模拟管理员会话：获取当前登录的管理员 ID 为 888L
        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(888L);

            // 严格适配源码：appealService.handleAppeal(id, adminId, action, adminNote)
            when(appealService.handleAppeal(eq(1L), eq(888L), eq("APPROVED"), eq("审核通过")))
                    .thenReturn(response);

            // 4. 发送请求并验证
            mockMvc.perform(put("/api/appeals/1/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("处理完成")) // 验证业务状态语
                    .andExpect(jsonPath("$.data.id").value(1L));
        }
    }
}