package com.example.campushelp.filter;

import com.example.campushelp.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        // 测试前确保上下文是干净的
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    public void tearDown() {
        // 测试后清理，防止污染其他测试
        SecurityContextHolder.clearContext();
    }

    // --- 场景 1：成功解析合法 Token（已增强断言覆盖） ---
    @Test
    public void shouldAuthenticateWhenTokenIsValid() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        // 打桩：未过期，且返回用户 ID 123
        when(jwtUtil.isTokenExpired(token)).thenReturn(false);
        when(jwtUtil.validateTokenAndGetUserId(token)).thenReturn(123L);

        // 执行 Filter
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 断言 1：SecurityContextHolder 中应该有了认证信息
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(123L);

        // 【新增断言】：验证过滤器是否正确赋予了该用户 "ROLE_USER" 权限角色
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        // 断言 2：必须调用了 filterChain.doFilter 让请求继续
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- 场景 2：Token 已过期 ---
    @Test
    public void shouldNotAuthenticateWhenTokenIsExpired() throws ServletException, IOException {
        String token = "expired.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        // 打桩：Token 已过期
        when(jwtUtil.isTokenExpired(token)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 断言：上下文保持为空
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- 场景 3：没有 Token 或者格式不对 ---
    @Test
    public void shouldIgnoreWhenAuthorizationHeaderIsMissingOrInvalid() throws ServletException, IOException {
        // 故意传一个格式错误的 Header
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 断言：不应该调用 jwtUtil，上下文也为空
        verifyNoInteractions(jwtUtil);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- 场景 4：JwtUtil 发生异常（容错测试） ---
    @Test
    public void shouldHandleExceptionAndContinueChain() throws ServletException, IOException {
        String token = "malformed.token";
        request.addHeader("Authorization", "Bearer " + token);

        // 模拟 jwtUtil 抛出运行时异常（比如签名伪造、格式损坏）
        when(jwtUtil.isTokenExpired(token)).thenThrow(new RuntimeException("JWT signature does not match"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 断言：即使报错，Security 依然放行（让 Spring Security 后续的机制去拦截，而不是 Filter 崩溃）
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }
}