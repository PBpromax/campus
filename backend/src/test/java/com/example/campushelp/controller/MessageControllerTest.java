package com.example.campushelp.controller;

import com.example.campushelp.dto.request.MessageRequest;
import com.example.campushelp.dto.response.MessageResponse;
import com.example.campushelp.service.MessageService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    @Test
    void testSendMessage_Success() throws Exception {
        MessageRequest request = new MessageRequest();
        request.setDemandId(1L);
        request.setReceiverId(2L);
        request.setContent("你好，请问需要带什么饭？");

        MessageResponse response = new MessageResponse();
        response.setId(10L);
        response.setContent("你好，请问需要带什么饭？");

        // 模拟静态安全上下文：获取当前登录用户 ID 为 100L
        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格对齐源码：messageService.sendMessage(userId, request)
            when(messageService.sendMessage(eq(100L), any(MessageRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testGetMessagesByDemand() throws Exception {
        List<MessageResponse> mockList = new ArrayList<>();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格对齐源码：messageService.getMessagesByDemand(demandId, userId)
            when(messageService.getMessagesByDemand(eq(1L), eq(100L))).thenReturn(mockList);

            mockMvc.perform(get("/api/messages/demand/1"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testGetMyConversations() throws Exception {
        List<MessageResponse> mockList = new ArrayList<>();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);

            // 严格对齐源码：messageService.getMyConversations(userId)
            when(messageService.getMyConversations(eq(100L))).thenReturn(mockList);

            mockMvc.perform(get("/api/messages/my"))
                    .andExpect(status().isOk());
        }
    }
}