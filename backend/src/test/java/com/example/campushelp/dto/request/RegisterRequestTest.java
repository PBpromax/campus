package com.example.campushelp.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRegisterRequest_ShouldPass() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("campus_user");
        request.setPassword("securepwd123");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testBlankUsernameAndPassword_ShouldFail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("   ");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        boolean hasUsernameBlankError = violations.stream().anyMatch(v -> v.getMessage().contains("用户名不能为空"));
        boolean hasPasswordBlankError = violations.stream().anyMatch(v -> v.getMessage().contains("密码不能为空"));

        assertTrue(hasUsernameBlankError);
        assertTrue(hasPasswordBlankError);
    }

    @Test
    void testUsernameLengthTooShort_ShouldFail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("a"); // 长度为1，小于 min=2
        request.setPassword("1234567");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        boolean hasUsernameSizeError = violations.stream().anyMatch(v -> v.getMessage().contains("用户名长度需在2-50位之间"));
        assertTrue(hasUsernameSizeError);
    }

    @Test
    void testPasswordLengthTooShort_ShouldFail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("12345"); // 长度为5，小于 min=6

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        boolean hasPasswordSizeError = violations.stream().anyMatch(v -> v.getMessage().contains("密码长度需在6-20位之间"));
        assertTrue(hasPasswordSizeError);
    }

    @Test
    void testPasswordLengthTooLong_ShouldFail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validUser");
        request.setPassword("123456789012345678901"); // 长度为21，大于 max=20

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        boolean hasPasswordSizeError = violations.stream().anyMatch(v -> v.getMessage().contains("密码长度需在6-20位之间"));
        assertTrue(hasPasswordSizeError);
    }
}