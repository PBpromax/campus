package com.example.campushelp.controller;

import com.example.campushelp.dto.request.CancelRequest;
import com.example.campushelp.dto.request.CompleteRequest;
import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.DemandResponse;
import com.example.campushelp.service.DemandService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // 👈 彻底绕过 JWT/Security 拦截，消灭 403 报错
public class DemandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DemandService demandService;

    @Test
    void testCreateDemand_Success() throws Exception {
        DemandRequest request = new DemandRequest();
        request.setTitle("帮忙带饭");
        request.setDescription("一份黄焖鸡");
        request.setCategory("跑腿");
        request.setExpectedTime(LocalDateTime.now().plusHours(2));
        request.setReward(new BigDecimal("5.00"));
        request.setLocation("一食堂");

        DemandResponse response = DemandResponse.builder().id(1L).title("帮忙带饭").status("PENDING").build();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            when(demandService.createDemand(eq(100L), any(DemandRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/demands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testGetDemandById() throws Exception {
        DemandResponse response = DemandResponse.builder().id(1L).title("帮忙带饭").build();
        when(demandService.getDemandById(anyLong())).thenReturn(response);

        mockMvc.perform(get("/api/demands/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllDemands() throws Exception {
        // 👈 【核心修复 1】：完美对应源码中的 getAllDemands(category, status, keyword)
        // 传入 3 个 any()，彻底解决“无法解析方法”编译报错
        List<DemandResponse> mockList = new ArrayList<>();
        when(demandService.getAllDemands(any(), any(), any())).thenReturn(mockList);

        mockMvc.perform(get("/api/demands")
                        .param("category", "跑腿")
                        .param("status", "PENDING")
                        .param("keyword", "带饭"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDemandsByStatus() throws Exception {
        // 👈 【核心修复 2】：完美对应源码中的 getDemandsByStatus(status)
        List<DemandResponse> mockList = new ArrayList<>();
        when(demandService.getDemandsByStatus(any())).thenReturn(mockList);

        mockMvc.perform(get("/api/demands/status/PENDING"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDemandsByCategory() throws Exception {
        // 👈 【补充】：对应源码中的 getDemandsByCategory(category)
        List<DemandResponse> mockList = new ArrayList<>();
        when(demandService.getDemandsByCategory(any())).thenReturn(mockList);

        mockMvc.perform(get("/api/demands/category/跑腿"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDemandsByPublisher() throws Exception {
        List<DemandResponse> mockList = new ArrayList<>();
        when(demandService.getDemandsByPublisher(anyLong())).thenReturn(mockList);

        mockMvc.perform(get("/api/demands/publisher/100"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDemandsByAcceptor() throws Exception {
        List<DemandResponse> mockList = new ArrayList<>();
        when(demandService.getDemandsByAcceptor(anyLong())).thenReturn(mockList);

        mockMvc.perform(get("/api/demands/acceptor/200"))
                .andExpect(status().isOk());
    }

    @Test
    void testAcceptDemand() throws Exception {
        DemandResponse response = DemandResponse.builder().id(1L).status("ACCEPTED").build();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            when(demandService.acceptDemand(eq(1L), eq(100L))).thenReturn(response);

            mockMvc.perform(put("/api/demands/1/accept"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testCompleteDemand() throws Exception {
        CompleteRequest request = new CompleteRequest();
        request.setProof("已经送到宿舍楼下图片凭证");
        DemandResponse response = DemandResponse.builder().id(1L).status("COMPLETED").build();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            // 完美匹配源码：demandService.completeDemand(id, userId, request.getProof())
            when(demandService.completeDemand(eq(1L), eq(100L), any())).thenReturn(response);

            mockMvc.perform(put("/api/demands/1/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testConfirmDemand() throws Exception {
        DemandResponse response = DemandResponse.builder().id(1L).status("CONFIRMED").build();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            when(demandService.confirmDemand(eq(1L), eq(100L))).thenReturn(response);

            mockMvc.perform(put("/api/demands/1/confirm"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testCancelDemand() throws Exception {
        CancelRequest request = new CancelRequest();
        request.setReason("临时有事不去了");
        DemandResponse response = DemandResponse.builder().id(1L).status("CANCELLED").build();

        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            // 完美匹配源码：demandService.cancelDemand(id, userId, reason)
            when(demandService.cancelDemand(eq(1L), eq(100L), any())).thenReturn(response);

            mockMvc.perform(put("/api/demands/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void testDeleteDemand() throws Exception {
        try (MockedStatic<SecurityUtils> mockedSecurity = Mockito.mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            doNothing().when(demandService).deleteDemand(eq(1L), eq(100L));

            mockMvc.perform(delete("/api/demands/1"))
                    .andExpect(status().isOk());
        }
    }
}