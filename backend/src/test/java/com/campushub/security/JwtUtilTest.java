package com.campushub.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void configuredKeyCanSignAndValidateUserIdentity() {
        JwtUtil jwt = new JwtUtil(randomSecret(), 60_000);
        String token = jwt.generateToken(42L);
        assertTrue(jwt.validateToken(token));
        assertEquals(42L, jwt.parseUserId(token));
    }

    @Test
    void changingConfiguredKeyRejectsOldToken() {
        JwtUtil first = new JwtUtil(randomSecret(), 60_000);
        JwtUtil second = new JwtUtil(randomSecret(), 60_000);
        assertFalse(second.validateToken(first.generateToken(42L)));
    }

    @Test
    void expiredOrMalformedTokenIsRejected() {
        JwtUtil expired = new JwtUtil(randomSecret(), -60_000);
        assertFalse(expired.validateToken(expired.generateToken(42L)));
        assertFalse(expired.validateToken("not-a-token"));
    }
}
