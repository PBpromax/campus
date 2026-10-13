package com.example.campushelp.util;

import com.example.campushelp.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityUtilsTest {

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        // 在每次测试前后彻底清理上下文，防止用例之间互相干扰
        SecurityContextHolder.clearContext();
    }

    // --- 场景一：未登录 ---
    @Test
    public void shouldThrow401WhenNotAuthenticated() {
        // 确保此时 Context 里面是空的
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录或登录已过期")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(401);
    }

    // --- 场景二：正常获取用户 ID ---
    @Test
    public void shouldReturnCurrentUserIdSuccessfully() {
        Long expectedUserId = 123L;
        // 构建一个已认证的凭证，并将 principal 设置为 Long 类型的用户 ID
        Authentication authentication = new UsernamePasswordAuthenticationToken(expectedUserId, null, null);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        assertThat(currentUserId).isEqualTo(expectedUserId);
    }

    // --- 场景三：Principal 类型错误（防御性边界测试） ---
    @Test
    public void shouldThrow401WhenPrincipalIsNotLong() {
        // 故意传入一个 String 类型的 Principal，而不是项目要求的 Long
        Authentication authentication = new UsernamePasswordAuthenticationToken("user_string_name", null, null);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的认证信息")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(401);
    }
}