package com.example.campushelp.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    public void setUp() {
        jwtUtil = new JwtUtil();
        // 使用 ReflectionTestUtils 模拟 Spring 的 @Value 注入
        ReflectionTestUtils.setField(jwtUtil, "secret", "my-test-secret-key-1234567890-secure");
        ReflectionTestUtils.setField(jwtUtil, "expireTime", 3600000L); // 1小时过期
    }

    @Test
    public void testGenerateAndValidateTokenSuccess() {
        Long userId = 99L;

        // 1. 生成 Token
        String token = jwtUtil.generateToken(userId);
        assertThat(token).isNotBlank();

        // 2. 验证 Token 并获取用户 ID
        Long parsedUserId = jwtUtil.validateTokenAndGetUserId(token);
        assertThat(parsedUserId).isEqualTo(userId);

        // 3. 验证是否过期
        boolean isExpired = jwtUtil.isTokenExpired(token);
        assertThat(isExpired).isFalse();
    }

    @Test
    public void testTokenExpired() {
        // 将过期时间设为负数（-1小时）来模拟生成一个一开始就过期的 Token
        ReflectionTestUtils.setField(jwtUtil, "expireTime", -3600000L);

        String token = jwtUtil.generateToken(88L);

        boolean isExpired = jwtUtil.isTokenExpired(token);
        assertThat(isExpired).isTrue();
    }

    @Test
    public void testInvalidToken() {
        String fakeToken = "this.is.a.fake.token";

        Long parsedUserId = jwtUtil.validateTokenAndGetUserId(fakeToken);
        assertThat(parsedUserId).isNull();

        boolean isExpired = jwtUtil.isTokenExpired(fakeToken);
        assertThat(isExpired).isTrue();
    }
}