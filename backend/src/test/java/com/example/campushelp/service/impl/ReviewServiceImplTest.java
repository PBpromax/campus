package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.ReviewRequest;
import com.example.campushelp.dto.response.ReviewResponse;
import com.example.campushelp.entity.Demand;
import com.example.campushelp.entity.Review;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import com.example.campushelp.repository.ReviewRepository;
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
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private DemandRepository demandRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    // ==========================================
    // 维度 1 & 2: 权限安全、校验拦截与关联对象不匹配边界
    // ==========================================

    @Test
    @DisplayName("TC_01: [权限拦截] 非当前需求订单参与方的第三方用户强行发起评价，必须拦截")
    void createReview_NotParticipant_ThrowsException() {
        Long hackerId = 99L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                reviewService.createReview(hackerId, request)
        );
        assertEquals("只有订单参与方才能评价", exception.getMessage());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("TC_02: [关联不匹配] 发布者发起评价，但是把评价目标对象指定为外部无关人员")
    void createReview_TargetUserMismatch_ThrowsException() {
        Long reviewerId = 1L; // 发布者
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(99L); // 错误的接收方目标 (理应是接单者2)

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                reviewService.createReview(reviewerId, request)
        );
        assertEquals("评价对象不正确", exception.getMessage());
    }

    @Test
    @DisplayName("TC_03: [前置状态] 订单尚处于在进行中阶段(非COMPLETED)，强行评价报错")
    void createReview_StatusNotCompleted_ThrowsException() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("IN_PROGRESS"); // 状态不符合

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                reviewService.createReview(reviewerId, request)
        );
        assertEquals("只能评价已完成的需求", exception.getMessage());
    }

    @Test
    @DisplayName("TC_04: [重复限制] 已经存在此用户对此订单的评价记录，阻断二次重复刷评")
    void createReview_AlreadyReviewed_ThrowsException() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(reviewRepository.existsByDemandIdAndReviewerId(100L, reviewerId)).thenReturn(true); // 已评价过

        BusinessException exception = assertThrows(BusinessException.class, () ->
                reviewService.createReview(reviewerId, request)
        );
        assertEquals("您已经评价过此订单", exception.getMessage());
    }

    // ==========================================
    // 维度 3: 状态转换与信用分阶梯演变业务规则 (Rating & Credit Rules)
    // ==========================================

    @Test
    @DisplayName("TC_05: [状态激励] 给予4-5星评价，目标用户信用分+1且维持正常未限制状态")
    void createReview_HighRating_CreditIncrements() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);
        request.setRating(5); // 5星高分
        request.setComment("小哥送的很快，好评！");

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setTitle("拿快递需求");
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        User reviewer = new User(); reviewer.setName("张三");
        User targetUser = new User(); targetUser.setId(2L); targetUser.setCreditScore(80); // 初始80分

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(reviewRepository.existsByDemandIdAndReviewerId(100L, reviewerId)).thenReturn(false);
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        ReviewResponse response = reviewService.createReview(reviewerId, request);

        assertNotNull(response);
        assertEquals(81, targetUser.getCreditScore(), "信用分正常激励自增+1");
        assertNull(targetUser.getRestrictedUntil(), "分值正常，绝对不该存在封禁时限限制");

        // 核心验证：下发了双份通知 (信用分变动 + 新评价通知)
        verify(notificationService, times(2)).createNotification(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("TC_06: [阶梯惩罚1] 给予1-2星评价扣3分，导致用户跌破60分大关，触发限时封禁3天")
    void createReview_LowRating_FallsBelow60_RestrictedThreeDays() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);
        request.setRating(2); // 2星差评
        request.setComment("超时了很久。");

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        User reviewer = new User(); reviewer.setName("张三");
        User targetUser = new User(); targetUser.setId(2L); targetUser.setCreditScore(62); // 跌落边界：62-3 = 59分

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.createReview(reviewerId, request);

        assertEquals(59, targetUser.getCreditScore(), "差评应被扣除3分信用分");
        assertNotNull(targetUser.getRestrictedUntil(), "跌破60分应触发账号限时约束封禁");
        assertTrue(targetUser.getRestrictedUntil().isAfter(LocalDateTime.now().plusHours(71)), "应该限时封禁约3天时间");
    }

    @Test
    @DisplayName("TC_07: [阶梯惩罚2] 给予1星恶评扣3分，导致用户分值跌破40极低底线，触发永久封禁(2099年)")
    void createReview_TerribleRating_FallsBelow40_PermanentBan() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);
        request.setRating(1); // 1星恶评
        request.setComment("态度恶劣！");

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        User reviewer = new User(); reviewer.setName("张三");
        User targetUser = new User(); targetUser.setId(2L); targetUser.setCreditScore(41); // 临界区: 41-3 = 38分

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.createReview(reviewerId, request);

        assertEquals(38, targetUser.getCreditScore());
        assertEquals(LocalDateTime.of(2099, 12, 31, 23, 59), targetUser.getRestrictedUntil(), "分值跌破40，强行锁定至2099年实现终身永久封禁");
    }

    @Test
    @DisplayName("TC_08: [状态平稳] 给予3星中立评价，信用分及封禁状态绝无任何扰动和多余通知")
    void createReview_ThreeStarRating_CreditStaysNeutral() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);
        request.setRating(3); // 3星中立

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        User reviewer = new User(); reviewer.setName("张三");
        User targetUser = new User(); targetUser.setId(2L); targetUser.setCreditScore(70);

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.createReview(reviewerId, request);

        assertEquals(70, targetUser.getCreditScore(), "3星评价：分数稳定不发生任何偏移");
        // 核心验证：仅下发了一条基础“收到新评价”普通通知，信用分变动通知在这个分支不该被触发
        verify(notificationService, times(1)).createNotification(eq(2L), eq("收到新评价"), anyString(), eq("REVIEW_RECEIVED"), any());
    }

    @Test
    @DisplayName("TC_09: [回暖解封] 5星激励令用户从59分重爬上60分，自动破除解除限时封禁状态")
    void createReview_CreditRecoverTo60_LiftsRestriction() {
        Long reviewerId = 1L;
        ReviewRequest request = new ReviewRequest();
        request.setDemandId(100L);
        request.setTargetUserId(2L);
        request.setRating(5); // 5星

        Demand demand = new Demand();
        demand.setId(100L);
        demand.setStatus("COMPLETED");
        demand.setPublisherId(1L);
        demand.setAcceptorId(2L);

        User reviewer = new User(); reviewer.setName("张三");
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setCreditScore(59); // 初始59分
        targetUser.setRestrictedUntil(LocalDateTime.now().plusDays(2)); // 原存续限时封禁

        when(demandRepository.findById(100L)).thenReturn(Optional.of(demand));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.createReview(reviewerId, request);

        assertEquals(60, targetUser.getCreditScore());
        assertNull(targetUser.getRestrictedUntil(), "分值重登安全线60分，原本存在的封禁惩罚条件自动撤销清除");
    }

    // ==========================================
    // 维度 4: 评价记录多视角聚合查询管线
    // ==========================================

    @Test
    @DisplayName("TC_10: [查询级联] 依靠需求ID精准追查抽取评价模型响应集合")
    void getReviewsByDemand_ReturnsList() {
        Review r = new Review();
        r.setId(1L);
        r.setDemandId(100L);
        r.setComment("正向内容");

        when(reviewRepository.findByDemandId(100L)).thenReturn(List.of(r));

        List<ReviewResponse> list = reviewService.getReviewsByDemand(100L);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("正向内容", list.get(0).getComment());
        verify(reviewRepository, times(1)).findByDemandId(100L);
    }

    @Test
    @DisplayName("TC_11: [查询级联] 依靠目标受评用户ID抽取个人收到的评价清单总流")
    void getReviewsByUser_ReturnsList() {
        Review r = new Review();
        r.setId(2L);
        r.setTargetUserId(10L);
        r.setComment("归属特定用户的服务回馈");

        when(reviewRepository.findByTargetUserId(10L)).thenReturn(List.of(r));

        List<ReviewResponse> list = reviewService.getReviewsByUser(10L);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("归属特定用户的服务回馈", list.get(0).getComment());
        verify(reviewRepository, times(1)).findByTargetUserId(10L);
    }
}