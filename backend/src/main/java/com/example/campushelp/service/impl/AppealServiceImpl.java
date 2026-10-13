package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.AppealRequest;
import com.example.campushelp.dto.response.AppealResponse;
import com.example.campushelp.entity.Appeal;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.AppealRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.AppealService;
import com.example.campushelp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public AppealResponse createAppeal(Long userId, AppealRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setUserName(user.getName());
        appeal.setType(request.getType());
        appeal.setReason(request.getReason());
        appeal.setStatus("PENDING");

        Appeal saved = appealRepository.save(appeal);

        return convertToResponse(saved);
    }

    @Override
    public List<AppealResponse> getMyAppeals(Long userId) {
        return appealRepository.findByUserId(userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppealResponse> getAllAppeals() {
        return appealRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppealResponse> getPendingAppeals() {
        return appealRepository.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppealResponse handleAppeal(Long appealId, Long adminId, String action, String adminNote) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException("申诉不存在"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException("该申诉已处理");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        if (!"ADMIN".equals(admin.getRole())) {
            throw new BusinessException(403, "只有管理员才能处理申诉");
        }

        if ("APPROVED".equals(action)) {
            appeal.setStatus("APPROVED");
            // 如果申诉类型是信用扣除，恢复5信用分
            if ("CREDIT_DEDUCTION".equals(appeal.getType())) {
                User user = userRepository.findById(appeal.getUserId())
                        .orElseThrow(() -> new BusinessException("用户不存在"));
                user.setCreditScore(user.getCreditScore() + 5);
                if (user.getCreditScore() >= 60 && user.getRestrictedUntil() != null) {
                    user.setRestrictedUntil(null);
                }
                userRepository.save(user);
                notificationService.createNotification(appeal.getUserId(),
                        "申诉通过", "您的信用分申诉已通过，信用分已恢复5分", "APPEAL_RESULT", appealId);
            } else if ("BAN".equals(appeal.getType())) {
                User user = userRepository.findById(appeal.getUserId())
                        .orElseThrow(() -> new BusinessException("用户不存在"));
                user.setRestrictedUntil(null);
                user.setCreditScore(60);
                userRepository.save(user);
                notificationService.createNotification(appeal.getUserId(),
                        "申诉通过", "您的封禁申诉已通过，账号已解封", "APPEAL_RESULT", appealId);
            }
        } else if ("REJECTED".equals(action)) {
            appeal.setStatus("REJECTED");
            notificationService.createNotification(appeal.getUserId(),
                    "申诉被驳回", "您的申诉已被驳回" + (adminNote != null ? "：" + adminNote : ""), "APPEAL_RESULT", appealId);
        } else {
            throw new BusinessException("无效的操作，请选择 APPROVED 或 REJECTED");
        }

        appeal.setAdminNote(adminNote);
        appeal.setHandledBy(adminId);
        Appeal saved = appealRepository.save(appeal);

        return convertToResponse(saved);
    }

    private AppealResponse convertToResponse(Appeal appeal) {
        return AppealResponse.builder()
                .id(appeal.getId())
                .userId(appeal.getUserId())
                .userName(appeal.getUserName())
                .type(appeal.getType())
                .reason(appeal.getReason())
                .status(appeal.getStatus())
                .adminNote(appeal.getAdminNote())
                .handledBy(appeal.getHandledBy())
                .createdAt(appeal.getCreatedAt())
                .updatedAt(appeal.getUpdatedAt())
                .build();
    }
}
