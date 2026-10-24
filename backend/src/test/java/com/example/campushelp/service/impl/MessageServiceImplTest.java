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
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private DemandRepository demandRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    // ==========================================
    // 1. 发送消息（sendMessage）业务场景
    // ==========================================

    @Test
    @DisplayName("TC_01: 发布者发送需求消息成功，数据和名称正确级联映射")
    void sendMessage_ByPublisher_Success() {
        // Arrange
        Long senderId = 1L;
        Long demandId = 100L;
        MessageRequest request = new MessageRequest();
        request.setDemandId(demandId);
        request.setReceiverId(2L);
        request.setContent("同学你好，请问我的快递可以帮送上楼吗？");

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L); // 匹配 senderId
        demand.setAcceptorId(2L);

        User sender = new User();
        sender.setId(senderId);
        sender.setName("发布者张三");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message msg = inv.getArgument(0);
            msg.setId(500L);
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        // Act
        MessageResponse response = messageService.sendMessage(senderId, request);

        // Assert：拒绝空洞，深度校验关键业务项结果
        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(demandId, response.getDemandId());
        assertEquals("发布者张三", response.getSenderName());
        assertEquals("同学你好，请问我的快递可以帮送上楼吗？", response.getContent());
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("TC_02: 接单者发送消息成功，内容及链路完整")
    void sendMessage_ByAcceptor_Success() {
        // Arrange
        Long senderId = 2L;
        Long demandId = 100L;
        MessageRequest request = new MessageRequest();
        request.setDemandId(demandId);
        request.setReceiverId(1L);
        request.setContent("没问题，已经在电梯里了。");

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L); // 匹配 senderId

        User sender = new User();
        sender.setId(senderId);
        sender.setName("接单者李四");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        MessageResponse response = messageService.sendMessage(senderId, request);

        // Assert
        assertNotNull(response);
        assertEquals("接单者李四", response.getSenderName());
        assertEquals("没问题，已经在电梯里了。", response.getContent());
    }

    @Test
    @DisplayName("TC_03: 权限校验-既非发布者也非接单者的无关用户强行发消息，必须拦截")
    void sendMessage_NotParticipant_ThrowsException() {
        // Arrange
        Long hackerId = 99L; // 外部越权账号
        Long demandId = 100L;
        MessageRequest request = new MessageRequest();
        request.setDemandId(demandId);

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.sendMessage(hackerId, request)
        );
        assertEquals("只有订单参与方才能发送消息", exception.getMessage());
        verify(messageRepository, never()).save(any(Message.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("TC_04: 异常输入-传入不存在的需求订单ID发送消息，阻断返回")
    void sendMessage_DemandNotFound_ThrowsException() {
        // Arrange
        MessageRequest request = new MessageRequest();
        request.setDemandId(404L); // 虚假单号

        when(demandRepository.findById(404L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.sendMessage(1L, request)
        );
        assertEquals("需求不存在", exception.getMessage());
    }

    @Test
    @DisplayName("TC_05: 异常输入-订单有效，但在用户表中找不到发送者实体")
    void sendMessage_UserNotFound_ThrowsException() {
        // Arrange
        Long senderId = 1L;
        Long demandId = 100L;
        MessageRequest request = new MessageRequest();
        request.setDemandId(demandId);

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(senderId)).thenReturn(Optional.empty()); // 数据库丢失此用户

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.sendMessage(senderId, request)
        );
        assertEquals("用户不存在", exception.getMessage());
    }

    // ==========================================
    // 2. 获取需求关联历史消息（getMessagesByDemand）业务场景
    // ==========================================

    @Test
    @DisplayName("TC_06: 权限校验-发布者查看历史消息，正常返回正序排列的数据")
    void getMessagesByDemand_AsPublisher_Success() {
        // Arrange
        Long demandId = 100L;
        Long userId = 1L; // 发布者本人

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(userId);
        demand.setAcceptorId(2L);

        Message msg = new Message();
        msg.setId(10L);
        msg.setContent("第一条消息");
        msg.setDemandId(demandId);

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(messageRepository.findByDemandIdOrderByCreatedAtAsc(demandId)).thenReturn(List.of(msg));

        // Act
        List<MessageResponse> list = messageService.getMessagesByDemand(demandId, userId);

        // Assert
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("第一条消息", list.getFirst().getContent());
    }

    @Test
    @DisplayName("TC_07: 权限校验-接单者查看历史消息，正常授权允许")
    void getMessagesByDemand_AsAcceptor_Success() {
        // Arrange
        Long demandId = 100L;
        Long userId = 2L; // 接单者本人

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setAcceptorId(userId);

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(messageRepository.findByDemandIdOrderByCreatedAtAsc(demandId)).thenReturn(Collections.emptyList());

        // Act
        List<MessageResponse> list = messageService.getMessagesByDemand(demandId, userId);

        // Assert
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("TC_08: 权限校验-非当前订单参与方窃听查看记录，强制拒绝")
    void getMessagesByDemand_NotParticipant_ThrowsException() {
        // Arrange
        Long demandId = 100L;
        Long hackerId = 99L; // 攻击第三方

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.getMessagesByDemand(demandId, hackerId)
        );
        assertEquals("只有订单参与方才能查看消息", exception.getMessage());
        verify(messageRepository, never()).findByDemandIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    @DisplayName("TC_09: 异常输入-输入缺失或错误的订单ID调取会话列表")
    void getMessagesByDemand_DemandNotFound_ThrowsException() {
        // Arrange
        when(demandRepository.findById(404L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.getMessagesByDemand(404L, 1L)
        );
        assertEquals("需求不存在", exception.getMessage());
    }

    // ==========================================
    // 3. 获取当前用户全部会话（getMyConversations）业务场景
    // ==========================================

    @Test
    @DisplayName("TC_10: 精准数据匹配-查询当前登录人的大厅总会话，保证管道打通并支持倒序排列")
    void getMyConversations_Success() {
        // Arrange
        Long userId = 5L;
        Message msgOut = new Message();
        msgOut.setId(201L);
        msgOut.setSenderId(userId);
        msgOut.setContent("发出去的信息项");

        Message msgIn = new Message();
        msgIn.setId(202L);
        msgIn.setReceiverId(userId);
        msgIn.setContent("收到的信息项");

        // Repository 联合多项多入参严格查询映射
        when(messageRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId))
                .thenReturn(List.of(msgIn, msgOut));

        // Act
        List<MessageResponse> totalConversations = messageService.getMyConversations(userId);

        // Assert
        assertNotNull(totalConversations);
        assertEquals(2, totalConversations.size());
        assertEquals("收到的信息项", totalConversations.get(0).getContent());
        assertEquals("发出去的信息项", totalConversations.get(1).getContent());
        verify(messageRepository, times(1)).findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
    }
    @Test
    void participantCannotMessageUnrelatedReceiver() {
        var demand=new Demand();demand.setPublisherId(1L);demand.setAcceptorId(2L);
        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        var request=new MessageRequest();request.setDemandId(100L);request.setReceiverId(99L);request.setContent("private");
        assertThrows(BusinessException.class,()->messageService.sendMessage(1L,request));
        verify(messageRepository,never()).save(any());
    }
    @Test
    void unacceptedTaskHasNoHumanRecipient() {
        var demand=new Demand();demand.setPublisherId(1L);
        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        var request=new MessageRequest();request.setDemandId(100L);request.setReceiverId(2L);request.setContent("private");
        assertThrows(BusinessException.class,()->messageService.sendMessage(1L,request));
        verify(messageRepository,never()).save(any());
    }
    @Test
    void oversizedHumanMessageDoesNotReachDatabase() {
        var demand=new Demand();demand.setPublisherId(1L);demand.setAcceptorId(2L);
        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        var request=new MessageRequest();request.setDemandId(100L);request.setReceiverId(2L);request.setContent("x".repeat(501));
        assertThrows(BusinessException.class,()->messageService.sendMessage(1L,request));
        verify(messageRepository,never()).save(any());
    }
}