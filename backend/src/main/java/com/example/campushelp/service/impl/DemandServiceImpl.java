package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.DemandResponse;
import com.example.campushelp.entity.Demand;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.DemandService;
import com.example.campushelp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandServiceImpl implements DemandService {

    private final DemandRepository demandRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public DemandResponse createDemand(Long userId, DemandRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 信用分限制检查
        if (user.getCreditScore() < 40) {
            throw new BusinessException("您的信用分过低，账号已被封禁");
        }
        if (user.getCreditScore() < 60) {
            if (user.getRestrictedUntil() != null && user.getRestrictedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException("您的信用分不足60，在 " +
                        user.getRestrictedUntil().toLocalDate() + " 前无法发布需求");
            }
        }
        if (user.getRestrictedUntil() != null && user.getRestrictedUntil().isAfter(LocalDateTime.now())
                && user.getCreditScore() < 40) {
            throw new BusinessException("您的账号已被封禁，无法发布需求");
        }

        Demand demand = new Demand();
        demand.setTitle(request.getTitle());
        demand.setDescription(request.getDescription());
        demand.setCategory(request.getCategory());
        demand.setExpectedTime(request.getExpectedTime());
        demand.setReward(request.getReward());
        demand.setLocation(request.getLocation());
        demand.setPublisherId(userId);
        demand.setPublisherName(user.getName());
        demand.setStatus("PENDING");
        demand.setPaymentStatus("UNPAID");

        Demand savedDemand = demandRepository.save(demand);

        return convertToResponse(savedDemand);
    }

    @Override
    public DemandResponse getDemandById(Long id) {
        Demand demand = demandRepository.findById(id)
                .orElseThrow(() -> new BusinessException("需求不存在"));
        return convertToResponse(demand);
    }

    @Override
    public List<DemandResponse> getAllDemands(String category, String status, String keyword) {
        String cat = (category != null && !category.isEmpty()) ? category : null;
        String st = (status != null && !status.isEmpty()) ? status : null;
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword : null;

        return demandRepository.searchDemands(cat, st, kw).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DemandResponse> getDemandsByStatus(String status) {
        return demandRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DemandResponse> getDemandsByCategory(String category) {
        return demandRepository.findByCategory(category).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DemandResponse> getDemandsByPublisher(Long publisherId) {
        return demandRepository.findByPublisherId(publisherId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DemandResponse> getDemandsByAcceptor(Long acceptorId) {
        return demandRepository.findByAcceptorId(acceptorId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DemandResponse acceptDemand(Long demandId, Long userId) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if (!"PENDING".equals(demand.getStatus())) {
            throw new BusinessException("该需求已被接单或已完成");
        }

        if (demand.getPublisherId().equals(userId)) {
            throw new BusinessException("不能接自己发布的需求");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 信用分封禁检查
        if (user.getCreditScore() < 40) {
            throw new BusinessException("您的信用分过低，账号已被封禁，无法接单");
        }

        demand.setAcceptorId(userId);
        demand.setAcceptorName(user.getName());
        demand.setStatus("IN_PROGRESS");
        demand.setPaymentStatus("HELD");

        Demand savedDemand = demandRepository.save(demand);

        // 通知发布者
        notificationService.createNotification(
                demand.getPublisherId(),
                "有人接单了",
                user.getName() + " 接取了您的需求「" + demand.getTitle() + "」",
                "ACCEPT",
                demandId
        );

        return convertToResponse(savedDemand);
    }

    @Override
    @Transactional
    public DemandResponse completeDemand(Long demandId, Long userId, String proof) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if (!"IN_PROGRESS".equals(demand.getStatus())) {
            throw new BusinessException("只能完成进行中的需求");
        }

        if (!userId.equals(demand.getAcceptorId())) {
            throw new BusinessException("只有接单者才能完成此需求");
        }

        demand.setCompletionProof(proof);
        demand.setStatus("PENDING_CONFIRMATION");

        Demand savedDemand = demandRepository.save(demand);

        // 通知发布者确认
        notificationService.createNotification(
                demand.getPublisherId(),
                "待确认完成",
                demand.getAcceptorName() + " 已提交完成凭证，请确认「" + demand.getTitle() + "」是否完成",
                "COMPLETE_CONFIRM",
                demandId
        );

        return convertToResponse(savedDemand);
    }

    @Override
    @Transactional
    public DemandResponse confirmDemand(Long demandId, Long userId) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if (!"PENDING_CONFIRMATION".equals(demand.getStatus())) {
            throw new BusinessException("该需求状态不允许确认");
        }

        if (!userId.equals(demand.getPublisherId())) {
            throw new BusinessException("只有发布者才能确认完成");
        }

        demand.setStatus("COMPLETED");
        demand.setPaymentStatus("RELEASED");

        Demand savedDemand = demandRepository.save(demand);

        // 通知接单者订单已完成
        notificationService.createNotification(
                demand.getAcceptorId(),
                "订单已完成",
                "发布者已确认「" + demand.getTitle() + "」完成，报酬已释放",
                "COMPLETE_CONFIRM",
                demandId
        );

        return convertToResponse(savedDemand);
    }

    @Override
    @Transactional
    public DemandResponse cancelDemand(Long demandId, Long userId, String reason) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if ("COMPLETED".equals(demand.getStatus())) {
            throw new BusinessException("已完成的需求不能取消");
        }

        if ("CANCELLED".equals(demand.getStatus())) {
            throw new BusinessException("该需求已被取消");
        }

        boolean isPublisher = userId.equals(demand.getPublisherId());
        boolean isAcceptor = userId.equals(demand.getAcceptorId());

        if (!isPublisher && !isAcceptor) {
            throw new BusinessException("只有发布者或接单者才能取消此需求");
        }

        if (isPublisher) {
            if (!"PENDING".equals(demand.getStatus())) {
                throw new BusinessException("已有接单者，无法取消，请联系接单者协商");
            }
            long minutesSinceCreate = java.time.Duration.between(
                    demand.getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceCreate > 30) {
                throw new BusinessException("发布超过30分钟，无法取消订单");
            }
            demand.setCanceledBy("PUBLISHER");
            demand.setPaymentStatus("REFUNDED");
        } else {
            if (!"IN_PROGRESS".equals(demand.getStatus()) && !"PENDING_CONFIRMATION".equals(demand.getStatus())) {
                throw new BusinessException("当前状态不允许取消");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new BusinessException("请填写取消理由");
            }
            demand.setCancelReason(reason.trim());
            demand.setCanceledBy("ACCEPTOR");
            demand.setPaymentStatus("REFUNDED");

            // 扣除接单者信用分
            User acceptor = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            int newScore = Math.max(0, acceptor.getCreditScore() - 5);
            acceptor.setCreditScore(newScore);

            // 信用分阈值检查
            if (newScore < 40) {
                acceptor.setRestrictedUntil(LocalDateTime.of(2099, 12, 31, 23, 59));
            } else if (newScore < 60 && (acceptor.getRestrictedUntil() == null
                    || acceptor.getRestrictedUntil().isBefore(LocalDateTime.now()))) {
                acceptor.setRestrictedUntil(LocalDateTime.now().plusDays(3));
            }
            userRepository.save(acceptor);

            // 通知接单者信用分变动
            notificationService.createNotification(userId,
                    "信用分变动",
                    "您因取消订单「" + demand.getTitle() + "」被扣除5信用分，当前信用分：" + newScore,
                    "CREDIT_CHANGE", demandId);

            // 通知发布者订单被取消
            notificationService.createNotification(demand.getPublisherId(),
                    "订单被取消",
                    demand.getAcceptorName() + " 取消了订单「" + demand.getTitle() + "」，理由：" + reason.trim(),
                    "CANCEL", demandId);
        }

        demand.setStatus("CANCELLED");
        demand.setCanceledAt(LocalDateTime.now());

        Demand savedDemand = demandRepository.save(demand);

        return convertToResponse(savedDemand);
    }

    @Override
    @Transactional
    public void deleteDemand(Long demandId, Long userId) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        if (!demand.getPublisherId().equals(userId)) {
            throw new BusinessException("只有发布者才能删除此需求");
        }

        demandRepository.deleteById(demandId);
    }

    private DemandResponse convertToResponse(Demand demand) {
        return DemandResponse.builder()
                .id(demand.getId())
                .title(demand.getTitle())
                .description(demand.getDescription())
                .category(demand.getCategory())
                .expectedTime(demand.getExpectedTime())
                .reward(demand.getReward())
                .location(demand.getLocation())
                .publisherId(demand.getPublisherId())
                .publisherName(demand.getPublisherName())
                .acceptorId(demand.getAcceptorId())
                .acceptorName(demand.getAcceptorName())
                .status(demand.getStatus())
                .completionProof(demand.getCompletionProof())
                .cancelReason(demand.getCancelReason())
                .canceledBy(demand.getCanceledBy())
                .canceledAt(demand.getCanceledAt())
                .paymentStatus(demand.getPaymentStatus())
                .createdAt(demand.getCreatedAt())
                .updatedAt(demand.getUpdatedAt())
                .build();
    }
}
