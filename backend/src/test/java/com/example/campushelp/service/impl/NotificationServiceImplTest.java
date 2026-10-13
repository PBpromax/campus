package com.example.campushelp.service.impl;

import com.example.campushelp.dto.response.NotificationResponse;
import com.example.campushelp.entity.Notification;
import com.example.campushelp.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    // ==========================================
    // 1. 创建通知 (createNotification)
    // ==========================================
    @Test
    @DisplayName("TC_01: [持久化] 创建通知，全量核心业务字段必须无缺失、正确落库")
    void createNotification_Success() {
        // Arrange
        Long userId = 1L;
        String title = "订单被接单";
        String content = "您的快递需求已被李四接单。";
        String type = "ACCEPT";
        Long relatedId = 100L;

        // 使用 ArgumentCaptor 捕获真实保存到数据库的实体，验证字段完整性
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        // Act
        notificationService.createNotification(userId, title, content, type, relatedId);

        // Assert：精准验证落库字段，杜绝无断言空洞
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification savedEntity = captor.getValue();

        assertNotNull(savedEntity);
        assertEquals(userId, savedEntity.getUserId());
        assertEquals(title, savedEntity.getTitle());
        assertEquals(content, savedEntity.getContent());
        assertEquals(type, savedEntity.getType());
        assertEquals(relatedId, savedEntity.getRelatedId());
        assertFalse(savedEntity.isRead(), "新建通知的初始已读状态必须为 false");
    }

    // ==========================================
    // 2. 获取我的通知 (getMyNotifications)
    // ==========================================
    @Test
    @DisplayName("TC_02: [数据时序] 拉取个人通知列表，成功返回按时间倒序排列的 DTO 集合")
    void getMyNotifications_Success() {
        // Arrange
        Long userId = 1L;
        Notification n = new Notification();
        n.setId(500L);
        n.setUserId(userId);
        n.setTitle("通知标题");
        n.setContent("通知内容");
        n.setType("SYSTEM");
        n.setRelatedId(200L);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));

        // Act
        List<NotificationResponse> result = notificationService.getMyNotifications(userId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        NotificationResponse response = result.get(0);
        assertEquals(500L, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("通知标题", response.getTitle());
        assertEquals("通知内容", response.getContent());
        assertEquals("SYSTEM", response.getType());
        assertEquals(200L, response.getRelatedId());
        assertFalse(response.isRead());
        verify(notificationRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ==========================================
    // 3. 获取未读通知数 (getUnreadCount)
    // ==========================================
    @Test
    @DisplayName("TC_03: [数据检索] 准确返回指定用户的未读通知数量统计")
    void getUnreadCount_Success() {
        // Arrange
        Long userId = 1L;
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(3);

        // Act
        int unreadCount = notificationService.getUnreadCount(userId);

        // Assert
        assertEquals(3, unreadCount);
        verify(notificationRepository, times(1)).countByUserIdAndIsReadFalse(userId);
    }

    // ==========================================
    // 4. 标记单条为已读 (markAsRead)
    // ==========================================
    @Test
    @DisplayName("TC_04: [状态转换] 拥有者更改属于自己的通知时，状态顺利变更为已读状态")
    void markAsRead_ByOwner_Success() {
        // Arrange
        Long notificationId = 10L;
        Long userId = 1L;

        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setUserId(userId); // 拥有者与当前操作用户一致
        notification.setRead(false);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // Act
        notificationService.markAsRead(notificationId, userId);

        // Assert
        assertTrue(notification.isRead(), "拥有者执行后，isRead 应当变为 true");
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    @DisplayName("TC_05: [权限安全] 外部黑客越权尝试修改别人的通知状态，必须进行数据状态隔离拦截")
    void markAsRead_ByOtherUser_ShouldDoNothing() {
        // Arrange
        Long notificationId = 10L;
        Long originalOwnerId = 1L;
        Long hackerUserId = 99L; // 越权恶意攻击ID

        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setUserId(originalOwnerId); // 属于用户 1
        notification.setRead(false);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // Act
        notificationService.markAsRead(notificationId, hackerUserId);

        // Assert：未通过所有权校验，数据不发生变动，且绝不应触发底层 save 数据库操作
        assertFalse(notification.isRead(), "非拥有者操作，状态绝不能被篡改");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("TC_06: [异常输入] 传入系统不存在的通知ID标记已读，应安全退出、不崩塌崩溃")
    void markAsRead_NotificationNotFound_ShouldSafeReturn() {
        // Arrange
        Long nonExistentId = 404L;
        Long userId = 1L;
        when(notificationRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> notificationService.markAsRead(nonExistentId, userId),
                "未查到数据时必须安全熔断，不能向外抛出 NullPointerException 等报错");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    // ==========================================
    // 5. 全部标记为已读 (markAllAsRead)
    // ==========================================
    @Test
    @DisplayName("TC_07: [批量转换] 一键清除全部未读，验证批量状态操作管道的打通")
    void markAllAsRead_Success() {
        // Arrange
        Long userId = 1L;
        doNothing().when(notificationRepository).markAllAsRead(userId);

        // Act
        notificationService.markAllAsRead(userId);

        // Assert
        verify(notificationRepository, times(1)).markAllAsRead(userId);
    }
}