package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.AppealRequest;
import com.example.campushelp.dto.response.AppealResponse;
import com.example.campushelp.entity.Appeal;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.AppealRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppealServiceImplTest {

    @Mock
    private AppealRepository appealRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AppealServiceImpl appealService;

    // ==========================================
    // createAppeal 测试用例
    // ==========================================

    @Test
    @DisplayName("TC_01: 用户存在，成功创建申诉")
    void createAppeal_Success() {
        Long userId = 1L;
        AppealRequest request = new AppealRequest();
        request.setType("CREDIT_DEDUCTION");
        request.setReason("误判扣分");

        User user = new User();
        user.setId(userId);
        user.setName("张三");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(invocation -> {
            Appeal appeal = invocation.getArgument(0);
            appeal.setId(100L); // 模拟自增ID
            return appeal;
        });

        AppealResponse response = appealService.createAppeal(userId, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("张三", response.getUserName());
        verify(appealRepository, times(1)).save(any(Appeal.class));
    }

    @Test
    @DisplayName("TC_02: 用户不存在，创建申诉失败抛出异常")
    void createAppeal_UserNotFound() {
        Long userId = 1L;
        AppealRequest request = new AppealRequest();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                appealService.createAppeal(userId, request)
        );
        assertEquals("用户不存在", exception.getMessage());
        verify(appealRepository, never()).save(any(Appeal.class));
    }

    // ==========================================
    // 查询方法 测试用例
    // ==========================================

    @Test
    @DisplayName("TC_03: 获取指定用户的申诉列表")
    void getMyAppeals_Success() {
        Long userId = 1L;
        Appeal appeal = new Appeal();
        appeal.setUserId(userId);

        when(appealRepository.findByUserId(userId)).thenReturn(List.of(appeal));

        List<AppealResponse> responses = appealService.getMyAppeals(userId);
        assertEquals(1, responses.size());
        assertEquals(userId, responses.getFirst().getUserId());
    }

    @Test
    @DisplayName("TC_05: 获取待处理的申诉列表")
    void getPendingAppeals_Success() {
        Appeal pendingAppeal = new Appeal();
        pendingAppeal.setStatus("PENDING");

        when(appealRepository.findByStatusOrderByCreatedAtDesc("PENDING"))
                .thenReturn(List.of(pendingAppeal));

        List<AppealResponse> responses = appealService.getPendingAppeals();
        assertEquals(1, responses.size());
        assertEquals("PENDING", responses.getFirst().getStatus());
    }

    // ==========================================
    // handleAppeal 测试用例
    // ==========================================

    @Test
    @DisplayName("TC_06: 申诉单不存在，处理失败")
    void handleAppeal_AppealNotFound() {
        when(appealRepository.findById(100L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                appealService.handleAppeal(100L, 1L, "APPROVED", "允许")
        );
        assertEquals("申诉不存在", exception.getMessage());
    }

    @Test
    @DisplayName("TC_07: 申诉已被处理过，再次处理失败")
    void handleAppeal_AlreadyHandled() {
        Appeal appeal = new Appeal();
        appeal.setStatus("APPROVED"); // 已经通过了

        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                appealService.handleAppeal(100L, 1L, "APPROVED", "再允许")
        );
        assertEquals("该申诉已处理", exception.getMessage());
    }

    @Test
    @DisplayName("TC_09: 操作人不是管理员，拒绝处理")
    void handleAppeal_NotAdmin() {
        Appeal appeal = new Appeal();
        appeal.setStatus("PENDING");

        User notAdmin = new User();
        notAdmin.setId(2L);
        notAdmin.setRole("USER"); // 普通用户

        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(notAdmin));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                appealService.handleAppeal(100L, 2L, "APPROVED", "备注")
        );
        assertEquals("只有管理员才能处理申诉", exception.getMessage());
        assertEquals(403, exception.getCode()); // 校验错误状态码
    }

    @Test
    @DisplayName("TC_11: 审批通过 - 信用扣除类型 - 分数恢复且触发解封")
    void handleAppeal_Approved_CreditDeduction() {
        // Arrange
        Appeal appeal = new Appeal();
        appeal.setId(100L);
        appeal.setUserId(10L);
        appeal.setStatus("PENDING");
        appeal.setType("CREDIT_DEDUCTION");

        User admin = new User();
        admin.setId(2L);
        admin.setRole("ADMIN");

        User user = new User();
        user.setId(10L);
        user.setCreditScore(55); // 原来55分（不及格被限制）
        user.setRestrictedUntil(LocalDateTime.now().plusDays(3));

        when(appealRepository.findById(100L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        AppealResponse response = appealService.handleAppeal(100L, 2L, "APPROVED", "情况属实");

        // Assert
        assertEquals("APPROVED", response.getStatus());
        assertEquals("情况属实", response.getAdminNote());
        assertEquals(2L, response.getHandledBy());

        assertEquals(60, user.getCreditScore()); // 55 + 5 = 60
        assertNull(user.getRestrictedUntil());   // 分数 >= 60，封禁应该被解除

        verify(userRepository, times(1)).save(user);
        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("申诉通过"), anyString(), eq("APPEAL_RESULT"), eq(100L)
        );
    }

    @Test
    @DisplayName("TC_12: 审批通过 - 账号封禁类型 - 强制解封恢复60分")
    void handleAppeal_Approved_Ban() {
        // Arrange
        Appeal appeal = new Appeal();
        appeal.setId(101L);
        appeal.setUserId(10L);
        appeal.setStatus("PENDING");
        appeal.setType("BAN");

        User admin = new User();
        admin.setId(2L);
        admin.setRole("ADMIN");

        User user = new User();
        user.setId(10L);
        user.setCreditScore(30);
        user.setRestrictedUntil(LocalDateTime.now().plusDays(7));

        when(appealRepository.findById(101L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        AppealResponse response = appealService.handleAppeal(101L, 2L, "APPROVED", "账号解封");

        // Assert
        assertEquals("APPROVED", response.getStatus());
        assertEquals(60, user.getCreditScore()); // 强制设定为 60
        assertNull(user.getRestrictedUntil());   // 解封
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("TC_13: 审批驳回 - 保持原样并发通知")
    void handleAppeal_Rejected() {
        // Arrange
        Appeal appeal = new Appeal();
        appeal.setId(102L);
        appeal.setUserId(10L);
        appeal.setStatus("PENDING");
        appeal.setType("BAN");

        User admin = new User();
        admin.setId(2L);
        admin.setRole("ADMIN");

        when(appealRepository.findById(102L)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        AppealResponse response = appealService.handleAppeal(102L, 2L, "REJECTED", "证据不足");

        // Assert
        assertEquals("REJECTED", response.getStatus());
        // 确保没有对 userRepository 进行任何 save 调用（即没动用户信息）
        verify(userRepository, never()).save(any(User.class));
        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("申诉被驳回"), contains("证据不足"), eq("APPEAL_RESULT"), eq(102L)
        );
    }
}