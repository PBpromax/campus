package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.DemandResponse;
import com.example.campushelp.entity.Demand;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemandServiceImplTest {

    @Mock
    private DemandRepository demandRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DemandServiceImpl demandService;

    // ==========================================
    // 1. 需求发布模块 (createDemand)
    // ==========================================

    @Test
    @DisplayName("TC_01: 信用良好，成功发布需求并初始化状态")
    void createDemand_Success() {
        Long userId = 1L;
        DemandRequest request = new DemandRequest();
        request.setTitle("帮拿快递");
        request.setCategory("DELIVERY");

        User user = new User();
        user.setId(userId);
        user.setCreditScore(80);
        user.setName("测试同学");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandResponse response = demandService.createDemand(userId, request);

        // 精细化断言：状态转换与初始化
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals("UNPAID", response.getPaymentStatus());
        assertEquals("测试同学", response.getPublisherName());
    }

    @Test
    @DisplayName("TC_02: 边界条件-信用分极低（低于40），限制发布")
    void createDemand_LowCredit_Ban() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setCreditScore(35); // 低于40

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                demandService.createDemand(userId, new DemandRequest()));
        assertTrue(ex.getMessage().contains("账号已被封禁"));
    }

    @Test
    @DisplayName("TC_03: 边界条件-信用分不足60且在限制惩罚期内，禁止发布")
    void createDemand_InRestrictedPeriod() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setCreditScore(55);
        user.setRestrictedUntil(LocalDateTime.now().plusDays(1)); // 惩罚期未满

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                demandService.createDemand(userId, new DemandRequest()));
        assertTrue(ex.getMessage().contains("无法发布需求"));
    }

    // ==========================================
    // 2. 接单控制模块 (acceptDemand)
    // ==========================================

    @Test
    @DisplayName("TC_04: 满足各项规则，正常接单并流转状态")
    void acceptDemand_Success() {
        Long demandId = 100L;
        Long acceptorId = 2L;

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setStatus("PENDING");

        User acceptor = new User();
        acceptor.setId(acceptorId);
        acceptor.setCreditScore(70);
        acceptor.setName("接单人B");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(acceptorId)).thenReturn(Optional.of(acceptor));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandResponse response = demandService.acceptDemand(demandId, acceptorId);

        // 关键断言检查
        assertEquals("IN_PROGRESS", response.getStatus());
        assertEquals("HELD", response.getPaymentStatus());
        assertEquals(acceptorId, response.getAcceptorId());
        verify(notificationService, times(1)).createNotification(any(), any(), any(), eq("ACCEPT"), eq(demandId));
    }

    @Test
    @DisplayName("TC_05: 权限校验-发布者不能接自己发布的需求")
    void acceptDemand_OwnDemand_ThrowsException() {
        Long demandId = 100L;
        Long userId = 1L;

        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(userId);
        demand.setStatus("PENDING"); // 【关键修复：必须明确设置为 PENDING 状态】

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        BusinessException ex = assertThrows(BusinessException.class, () -> demandService.acceptDemand(demandId, userId));
        assertEquals("不能接自己发布的需求", ex.getMessage());
    }

    @Test
    @DisplayName("TC_06: 异常输入-抢接已被接单或完成的需求，拦截处理")
    void acceptDemand_NotPending_ThrowsException() {
        Long demandId = 100L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setStatus("IN_PROGRESS"); // 状态不是 PENDING

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        BusinessException ex = assertThrows(BusinessException.class, () -> demandService.acceptDemand(demandId, 2L));
        assertEquals("该需求已被接单或已完成", ex.getMessage());
    }

    @Test
    @DisplayName("TC_07: 业务规则-接单人信用分低于40，直接限制接单")
    void acceptDemand_AcceptorLowCredit_ThrowsException() {
        Long demandId = 100L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setStatus("PENDING");

        User acceptor = new User();
        acceptor.setId(2L);
        acceptor.setCreditScore(39); // 跌破40门槛

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptor));

        BusinessException ex = assertThrows(BusinessException.class, () -> demandService.acceptDemand(demandId, 2L));
        assertTrue(ex.getMessage().contains("无法接单"));
    }

    // ==========================================
    // 3. 完工与验收流转模块 (completeDemand / confirmDemand)
    // ==========================================

    @Test
    @DisplayName("TC_08: 接单者成功提交完成凭证，转换状态至待确认")
    void completeDemand_Success() {
        Long demandId = 100L;
        Long acceptorId = 2L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setAcceptorId(acceptorId);
        demand.setStatus("IN_PROGRESS");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandResponse response = demandService.completeDemand(demandId, acceptorId, "proof_url");

        assertEquals("PENDING_CONFIRMATION", response.getStatus());
        assertEquals("proof_url", response.getCompletionProof());
    }

    @Test
    @DisplayName("TC_09: 权限校验-非该需求接单者尝试交付，拦截")
    void completeDemand_WrongUser_ThrowsException() {
        Long demandId = 100L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setAcceptorId(2L); // 真实接单人是2
        demand.setStatus("IN_PROGRESS");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        assertThrows(BusinessException.class, () -> demandService.completeDemand(demandId, 3L, "proof"));
    }

    @Test
    @DisplayName("TC_10: 发布者确认无误，释放资金，状态流转为终结态")
    void confirmDemand_Success() {
        Long demandId = 100L;
        Long publisherId = 1L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(publisherId);
        demand.setStatus("PENDING_CONFIRMATION");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandResponse response = demandService.confirmDemand(demandId, publisherId);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("RELEASED", response.getPaymentStatus());
    }

    @Test
    @DisplayName("TC_11: 权限校验-非发布者尝试强行确认订单，予以拦截")
    void confirmDemand_NotPublisher_ThrowsException() {
        Long demandId = 100L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(1L);
        demand.setStatus("PENDING_CONFIRMATION");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        assertThrows(BusinessException.class, () -> demandService.confirmDemand(demandId, 99L));
    }

    // ==========================================
    // 4. 取消需求策略与违约扣分模块 (cancelDemand)
    // ==========================================

    @Test
    @DisplayName("TC_12: 时效控制-发布者在30分钟免责期内安全取消")
    void cancelDemand_Publisher_WithinThirtyMinutes() {
        Long demandId = 100L;
        Long publisherId = 1L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(publisherId);
        demand.setStatus("PENDING");
        demand.setCreatedAt(LocalDateTime.now().minusMinutes(15)); // 15分钟前发布

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandResponse response = demandService.cancelDemand(demandId, publisherId, null);

        assertEquals("CANCELLED", response.getStatus());
        assertEquals("REFUNDED", response.getPaymentStatus());
        assertEquals("PUBLISHER", response.getCanceledBy());
    }

    @Test
    @DisplayName("TC_13: 时效控制-发布者发布超过30分钟，硬性拦截取消")
    void cancelDemand_Publisher_Timeout() {
        Long demandId = 100L;
        Long publisherId = 1L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setPublisherId(publisherId);
        demand.setStatus("PENDING");
        demand.setCreatedAt(LocalDateTime.now().minusMinutes(45)); // 45分钟前发布

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                demandService.cancelDemand(demandId, publisherId, null));
        assertTrue(ex.getMessage().contains("发布超过30分钟"));
    }

    @Test
    @DisplayName("TC_14: 信用分规则-接单者中途违约取消，扣减5分并触发3天阶梯处罚")
    void cancelDemand_Acceptor_PunishThreeDays() {
        Long demandId = 100L;
        Long acceptorId = 2L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setAcceptorId(acceptorId);
        demand.setStatus("IN_PROGRESS");

        User acceptor = new User();
        acceptor.setId(acceptorId);
        acceptor.setCreditScore(63); // 扣5分后剩58分，触碰低于60惩罚点

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(acceptorId)).thenReturn(Optional.of(acceptor));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        demandService.cancelDemand(demandId, acceptorId, "有急事无法完成");

        assertEquals(58, acceptor.getCreditScore());
        assertNotNull(acceptor.getRestrictedUntil());
        assertTrue(acceptor.getRestrictedUntil().isAfter(LocalDateTime.now()));
        verify(userRepository, times(1)).save(acceptor);
    }

    @Test
    @DisplayName("TC_15: 信用分规则-接单者违约导致分数跌破40，触发永久封禁边界")
    void cancelDemand_Acceptor_PermanentBan() {
        Long demandId = 100L;
        Long acceptorId = 2L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setAcceptorId(acceptorId);
        demand.setStatus("IN_PROGRESS");

        User acceptor = new User();
        acceptor.setId(acceptorId);
        acceptor.setCreditScore(42); // 扣5分后剩37分，击穿40底线

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));
        when(userRepository.findById(acceptorId)).thenReturn(Optional.of(acceptor));
        when(demandRepository.save(any(Demand.class))).thenAnswer(inv -> inv.getArgument(0));

        demandService.cancelDemand(demandId, acceptorId, "不给送了");

        assertEquals(37, acceptor.getCreditScore());
        assertEquals(2099, acceptor.getRestrictedUntil().getYear()); // 卡死2099终极边界点
    }

    @Test
    @DisplayName("TC_16: 异常输入-接单者违约取消时未输入合法取消原因")
    void cancelDemand_AcceptorEmptyReason_ThrowsException() {
        Long demandId = 100L;
        Demand demand = new Demand();
        demand.setId(demandId);
        demand.setAcceptorId(2L);
        demand.setStatus("IN_PROGRESS");

        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demand));

        assertThrows(BusinessException.class, () -> demandService.cancelDemand(demandId, 2L, "  "));
    }

    // ==========================================
    // 5. 数据的删除与各种查询边缘覆盖
    // ==========================================

    @Test
    @DisplayName("TC_17: 权限校验-非发布者尝试删除他人订单，予以拒绝")
    void deleteDemand_NotPublisher_ThrowsException() {
        Demand demand = new Demand();
        demand.setId(100L);
        demand.setPublisherId(1L); // 属于用户1

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));

        assertThrows(BusinessException.class, () -> demandService.deleteDemand(100L, 99L));
        verify(demandRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("TC_18: 异常输入-查询不存在的订单 ID，转换报错")
    void getDemandById_NotFound() {
        when(demandRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> demandService.getDemandById(999L));
    }

    @Test
    @DisplayName("TC_19: 异常/空项输入-多条件条件动态检索边界覆盖")
    void getAllDemands_NullQueryEdge() {
        Demand demand = new Demand();
        // 使用 any() 允许接收任何转换后的参数，或者精准匹配传入 (null, "  ", null)
        when(demandRepository.searchDemands(any(), any(), any())).thenReturn(List.of(demand));

        List<DemandResponse> results = demandService.getAllDemands("", "  ", null);
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("TC_20: 基础数据查询方法管道打通（全状态覆盖）")
    void getDemandsByVariousFields_Success() {
        Demand d = new Demand();
        when(demandRepository.findByStatusOrderByCreatedAtDesc("ST")).thenReturn(List.of(d));
        when(demandRepository.findByCategory("CAT")).thenReturn(List.of(d));
        when(demandRepository.findByPublisherId(1L)).thenReturn(List.of(d));
        when(demandRepository.findByAcceptorId(2L)).thenReturn(List.of(d));

        assertFalse(demandService.getDemandsByStatus("ST").isEmpty());
        assertFalse(demandService.getDemandsByCategory("CAT").isEmpty());
        assertFalse(demandService.getDemandsByPublisher(1L).isEmpty());
        assertFalse(demandService.getDemandsByAcceptor(2L).isEmpty());
    }
}