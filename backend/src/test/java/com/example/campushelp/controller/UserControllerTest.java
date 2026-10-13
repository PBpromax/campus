package com.example.campushelp.controller;

import com.example.campushelp.dto.request.RegisterRequest;
import com.example.campushelp.dto.request.UserInfoRequest;
import com.example.campushelp.dto.response.UserResponse;
import com.example.campushelp.service.UserService;
import com.example.campushelp.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // 放行安全过滤器，彻底避免单测报 403 错误
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    /**
     * 测试用户注册接口
     */
    @Test
    void testRegister_Success() throws Exception {
        // 1. 构造请求体 DTO
        RegisterRequest request = new RegisterRequest();
        // 👈【核心修复】：补充校验框架要求的必填字段
        request.setUsername("testuser_reg");
        request.setPassword("123456");

        // 2. 构造预期返回的用户数据
        UserResponse mockUser = new UserResponse();
        mockUser.setId(100L);

        // 3. 模拟 Service 和 JwtUtil 行为
        when(userService.register(any(RegisterRequest.class))).thenReturn(mockUser);
        when(jwtUtil.generateToken(eq(100L))).thenReturn("mock-jwt-token-123");

        // 4. 执行 MockMvc 请求并验证
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token-123"))
                .andExpect(jsonPath("$.data.user.id").value(100L));
    }
    /**
     * 测试用户登录接口
     */
    @Test
    void testLogin_Success() throws Exception {
        // 1. 构造 Map 类型的请求参数
        Map<String, String> request = new HashMap<>();
        request.put("username", "testuser1");
        request.put("password", "123456");

        // 2. 构造预期返回的用户数据
        UserResponse mockUser = new UserResponse();
        mockUser.setId(100L);

        // 3. 模拟 Service 和 JwtUtil 行为
        when(userService.login(eq("testuser1"), eq("123456"))).thenReturn(mockUser);
        when(jwtUtil.generateToken(eq(100L))).thenReturn("mock-jwt-token-456");

        // 4. 执行请求并验证
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("登录成功")) // 验证状态语
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token-456"))
                .andExpect(jsonPath("$.data.user.id").value(100L));
    }

    /**
     * 测试获取用户信息接口
     */
    @Test
    void testGetUserInfo() throws Exception {
        UserResponse mockUser = new UserResponse();
        mockUser.setId(100L);

        // 模拟根据 ID 获取用户信息
        when(userService.getUserInfo(eq(100L))).thenReturn(mockUser);

        mockMvc.perform(get("/api/users/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100L));
    }

    /**
     * 测试修改用户信息接口
     */
    @Test
    void testUpdateUserInfo_Success() throws Exception {
        // 1. 构造请求体 DTO
        UserInfoRequest request = new UserInfoRequest();
        // 👈【核心修复】：补充更新资料时后端强校验的三个必填字段
        request.setName("张三");
        request.setCollege("计算机学院");
        request.setMajor("软件工程");

        UserResponse mockUser = new UserResponse();
        mockUser.setId(100L);

        // 2. 模拟更新操作
        when(userService.updateUserInfo(eq(100L), any(UserInfoRequest.class))).thenReturn(mockUser);

        // 3. 发送请求并断言
        mockMvc.perform(put("/api/users/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.id").value(100L));
    }

    /**
     * 测试删除用户接口
     */
    @Test
    void testDeleteUser() throws Exception {
        // 模拟底层删除服务返回 void 时的逻辑
        doNothing().when(userService).deleteUser(eq(100L));

        mockMvc.perform(delete("/api/users/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("删除成功")); // 验证状态语
    }
}