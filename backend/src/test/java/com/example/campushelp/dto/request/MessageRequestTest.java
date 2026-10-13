package com.example.campushelp.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MessageRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化原生的 Validator 校验器，无需启动 Spring 环境，运行速度极快
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("正向测试：当所有参数完整且合法时，校验应该通过")
    void testValidMessageRequest_ShouldPass() {
        // 准备合法的请求数据
        MessageRequest request = new MessageRequest();
        request.setDemandId(101L);
        request.setReceiverId(202L);
        request.setContent("你好，请问快递拿到了吗？");

        // 执行校验
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // 断言：不应该有任何校验错误
        assertTrue(violations.isEmpty(), "合法的消息请求不应该触发校验错误");
    }

    @Test
    @DisplayName("反向测试：当 demandId 为 null 时，校验应该失败")
    void testNullDemandId_ShouldFail() {
        MessageRequest request = new MessageRequest();
        request.setDemandId(null); // 触发 @NotNull
        request.setReceiverId(202L);
        request.setContent("已经出发去拿了");

        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "demandId 为 null 时校验应该失败");
        boolean hasDemandIdError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("demandId"));
        assertTrue(hasDemandIdError, "错误应该定位在 demandId 字段上");
    }

    @Test
    @DisplayName("反向测试：当 receiverId 为 null 时，校验应该失败")
    void testNullReceiverId_ShouldFail() {
        MessageRequest request = new MessageRequest();
        request.setDemandId(101L);
        request.setReceiverId(null); // 触发 @NotNull
        request.setContent("已经出发去拿了");

        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "receiverId 为 null 时校验应该失败");
        boolean hasReceiverIdError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("receiverId"));
        assertTrue(hasReceiverIdError, "错误应该定位在 receiverId 字段上");
    }

    @Test
    @DisplayName("反向测试：当 content 为空、纯空格或 null 时，校验应该失败")
    void testInvalidContent_ShouldFail() {
        // 1. 测试 content 为空字符串 ""
        MessageRequest request1 = new MessageRequest();
        request1.setDemandId(101L);
        request1.setReceiverId(202L);
        request1.setContent(""); // 触发 @NotBlank
        Set<ConstraintViolation<MessageRequest>> violations1 = validator.validate(request1);
        assertFalse(violations1.isEmpty(), "content 为空字符串时校验应该失败");

        // 2. 测试 content 为纯空格字符串 "   "
        MessageRequest request2 = new MessageRequest();
        request2.setDemandId(101L);
        request2.setReceiverId(202L);
        request2.setContent("     "); // 触发 @NotBlank
        Set<ConstraintViolation<MessageRequest>> violations2 = validator.validate(request2);
        assertFalse(violations2.isEmpty(), "content 为纯空格时校验应该失败");

        // 3. 测试 content 为 null
        MessageRequest request3 = new MessageRequest();
        request3.setDemandId(101L);
        request3.setReceiverId(202L);
        request3.setContent(null); // 触发 @NotBlank
        Set<ConstraintViolation<MessageRequest>> violations3 = validator.validate(request3);
        assertFalse(violations3.isEmpty(), "content 为 null 时校验应该失败");

        boolean hasContentError = violations3.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content"));
        assertTrue(hasContentError, "错误应该定位在 content 字段上");
    }

    @Test
    @DisplayName("反向测试：当所有字段全部为空时，应该触发多个校验错误")
    void testAllFieldsInvalid_ShouldFail() {
        MessageRequest request = new MessageRequest();
        request.setDemandId(null);
        request.setReceiverId(null);
        request.setContent("");

        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // 断言：存在错误，且错误总数量为 3
        assertFalse(violations.isEmpty());
        assertEquals(3, violations.size(), "应该有 3 个字段全部触发校验拦截");
    }
}