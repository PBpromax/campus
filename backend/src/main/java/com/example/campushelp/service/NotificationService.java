package com.example.campushelp.service;

import com.example.campushelp.dto.response.NotificationResponse;
import java.util.List;

public interface NotificationService {
    void createNotification(Long userId, String title, String content, String type, Long relatedId);
    List<NotificationResponse> getMyNotifications(Long userId);
    int getUnreadCount(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}
