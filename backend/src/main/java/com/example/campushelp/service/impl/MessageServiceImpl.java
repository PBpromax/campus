package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.MessageRequest;
import com.example.campushelp.dto.response.MessageResponse;
import com.example.campushelp.entity.Demand;
import com.example.campushelp.entity.Message;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import com.example.campushelp.repository.MessageRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final DemandRepository demandRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        Demand demand = demandRepository.findById(request.getDemandId())
                .orElseThrow(() -> new BusinessException("需求不存在"));

        // 只有发布者和接单者可以发消息
        if (!senderId.equals(demand.getPublisherId()) && !senderId.equals(demand.getAcceptorId())) {
            throw new BusinessException("只有订单参与方才能发送消息");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Long counterpart = senderId.equals(demand.getPublisherId()) ? demand.getAcceptorId() : demand.getPublisherId();
        if (counterpart == null) throw new BusinessException("任务尚未接单，请先自行联系同学协商或等待接单");
        if (!counterpart.equals(request.getReceiverId())) throw new BusinessException("只能向当前任务的另一位参与者发送消息");
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().length() > 500)
            throw new BusinessException("消息需要1—500字");

        Message message = new Message();
        message.setDemandId(request.getDemandId());
        message.setSenderId(senderId);
        message.setSenderName(sender.getName() == null || sender.getName().isBlank() ? sender.getUsername() : sender.getName());
        message.setReceiverId(request.getReceiverId());
        message.setContent(request.getContent());

        Message saved = messageRepository.save(message);
        return convertToResponse(saved);
    }

    @Override
    public List<MessageResponse> getMessagesByDemand(Long demandId, Long userId) {
        Demand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new BusinessException("需求不存在"));

        // 只有订单参与方可以查看消息
        if (!userId.equals(demand.getPublisherId()) && !userId.equals(demand.getAcceptorId())) {
            throw new BusinessException("只有订单参与方才能查看消息");
        }

        return messageRepository.findByDemandIdOrderByCreatedAtAsc(demandId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageResponse> getMyConversations(Long userId) {
        return messageRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private MessageResponse convertToResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .demandId(message.getDemandId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
