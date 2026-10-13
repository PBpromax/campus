package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.RegisterRequest;
import com.example.campushelp.dto.request.UserInfoRequest;
import com.example.campushelp.dto.response.UserResponse;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 全业务场景单元测试")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Nested
    @DisplayName("1. 用户注册测试架构 (register)")
    class RegisterTest {

        @Test
        @DisplayName("TC_01: [状态转换] 注册常规新用户，完成密码哈希并级联初始化满分100分")
        void register_Success() {
            // Arrange (完全适配源码：使用 username 与 password)
            RegisterRequest request = new RegisterRequest();
            request.setUsername("testUser123");
            request.setPassword("plainPassword");

            when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
            when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            // Act
            UserResponse response = userService.register(request);

            // Assert：全字段精准断言验证结果
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("testUser123");
            assertThat(response.getCreditScore()).isEqualTo(100); // 必须被源码初始化为100分
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("TC_02: [边界拦截] 唯一性边界冲突，使用已有用户名注册引发阻断异常")
        void register_DuplicateUsername_ThrowsException() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existingUser");

            when(userRepository.existsByUsername("existingUser")).thenReturn(true);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名已被注册");
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("2. 个人档案修改测试架构 (updateUserInfo)")
    class UpdateUserInfoTest {

        @Test
        @DisplayName("TC_03: [数据映射] 个人公开信息档案重写，学号、专业等字段必须级联同步修改")
        void updateUserInfo_Success() {
            // Arrange
            Long userId = 1L;
            UserInfoRequest request = new UserInfoRequest();
            request.setName("新姓名");
            request.setStudentId("20260101");
            request.setPhone("13912345678");
            request.setCollege("计算机学院");
            request.setMajor("软件工程");

            User existingUser = new User();
            existingUser.setId(userId);
            existingUser.setUsername("testUser");
            existingUser.setCreditScore(95);

            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act —— 【已对齐实际源码签名参数顺序：第一个参数传入 userId，第二个参数传入 request】
            UserResponse response = userService.updateUserInfo(userId, request);

            // Assert：精准验证数据流转换完整性
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("新姓名");
            assertThat(response.getStudentId()).isEqualTo("20260101");
            assertThat(response.getPhone()).isEqualTo("13912345678");
            assertThat(response.getCollege()).isEqualTo("计算机学院");
            assertThat(response.getMajor()).isEqualTo("软件工程");
            assertThat(response.getUsername()).isEqualTo("testUser"); // 账户名保持不变
            verify(userRepository, times(1)).save(existingUser);
        }

        @Test
        @DisplayName("TC_04: [异常输入] 尝试对不存在的用户进行更新，系统必须快速拒绝失败")
        void updateUserInfo_UserNotFound_ThrowsException() {
            // Arrange
            Long userId = 404L;
            UserInfoRequest request = new UserInfoRequest();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert —— 【已对齐实际源码签名参数顺序】
            assertThatThrownBy(() -> userService.updateUserInfo(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("3. 发布权限及信用规则拦截测试架构 (checkPublishPermission)")
    class CheckPublishPermissionTest {

        @Test
        @DisplayName("TC_05: [信用拦截] 严重惩罚边界-信用分极度匮乏(<40)，直接死锁封禁")
        void checkPermission_ScoreBelow40_ThrowsException() {
            User user = new User();
            user.setCreditScore(35); // 跌落极值线

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.checkPublishPermission(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("您的信用分过低，账号已被封禁");
        }

        @Test
        @DisplayName("TC_06: [信用拦截] 阶梯惩罚时效-信用分处于40-60区间且尚未到限制解封期")
        void checkPermission_LowCreditWithRestrictedTime_ThrowsException() {
            User user = new User();
            user.setCreditScore(50);
            user.setRestrictedUntil(LocalDateTime.now().plusDays(1)); // 封禁尚未到期

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.checkPublishPermission(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无法发布需求");
        }

        @Test
        @DisplayName("TC_07: [边界特征] 逻辑现状显露-低于60分但解禁时限为null时会静默放行")
        void checkPermission_LowCreditWithNullRestrictedTime_PassesSilently() {
            User user = new User();
            user.setCreditScore(50);
            user.setRestrictedUntil(null); // 时限为空

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // 精准对齐源码现状：因源码把 restrictedUntil != null 当做前置触发器，这里会正常放行通过
            assertDoesNotThrow(() -> userService.checkPublishPermission(1L));
        }

        @Test
        @DisplayName("TC_08: [权限正向] 信用健康达标(如85分)，权限校验流畅通行")
        void checkPermission_NormalUser_Success() {
            User user = new User();
            user.setCreditScore(85);
            user.setRestrictedUntil(null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertDoesNotThrow(() -> userService.checkPublishPermission(1L));
        }
    }

    @Nested
    @DisplayName("4. 用户销户测试架构 (deleteUser)")
    class DeleteUserTest {

        @Test
        @DisplayName("TC_09: [销户清除] 用户确定存续时，安全调用持久层删除数据")
        void deleteUser_Success() {
            when(userRepository.existsById(1L)).thenReturn(true);

            userService.deleteUser(1L);

            verify(userRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("TC_10: [异常阻断] 被注销用户本就不存在时引发清除边界保护报错")
        void deleteUser_NotExist_ThrowsException() {
            when(userRepository.existsById(404L)).thenReturn(false);

            assertThatThrownBy(() -> userService.deleteUser(404L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
            verify(userRepository, never()).deleteById(anyLong());
        }
    }
}