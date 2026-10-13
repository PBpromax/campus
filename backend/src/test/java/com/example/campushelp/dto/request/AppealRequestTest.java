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

class AppealRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化原生的 Validator 校验器，无需启动 Spring 环境，运行速度极快
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("正向测试：当 type 和 reason 都合法时，校验应该通过")
    void testValidAppealRequest_ShouldPass() {
        // 准备合法的请求数据
        AppealRequest request = new AppealRequest();
        request.setType("订单纠纷");
        request.setReason("接单人未按照约定时间送达，且拒绝沟通。");

        // 执行校验
        Set<ConstraintViolation<AppealRequest>> violations = validator.validate(request);

        // 断言：不应该有任何校验错误
        assertTrue(violations.isEmpty(), "合法的申诉请求不应该触发校验错误");
    }

    @Test
    @DisplayName("反向测试：当 type 为空、空格或 null 时，校验应该失败")
    void testInvalidType_ShouldFail() {
        // 1. 测试 type 为空字符串
        AppealRequest request1 = new AppealRequest();
        request1.setType("");
        request1.setReason("正常填写申诉原因");
        Set<ConstraintViolation<AppealRequest>> violations1 = validator.validate(request1);
        assertFalse(violations1.isEmpty(), "type 为空字符串时校验应该失败");
        assertTrue(violations1.stream().anyMatch(v -> v.getPropertyPath().toString().equals("type")));

        // 2. 测试 type 为纯空格字符串
        AppealRequest request2 = new AppealRequest();
        request2.setType("   ");
        request2.setReason("正常填写申诉原因");
        Set<ConstraintViolation<AppealRequest>> violations2 = validator.validate(request2);
        assertFalse(violations2.isEmpty(), "type 为纯空格时校验应该失败");

        // 3. 测试 type 为 null
        AppealRequest request3 = new AppealRequest();
        request3.setType(null);
        request3.setReason("正常填写申诉原因");
        Set<ConstraintViolation<AppealRequest>> violations3 = validator.validate(request3);
        assertFalse(violations3.isEmpty(), "type 为 null 时校验应该失败");
    }

    @Test
    @DisplayName("反向测试：当 reason 为空、空格或 null 时，校验应该失败")
    void testInvalidReason_ShouldFail() {
        // 1. 测试 reason 为空字符串
        AppealRequest request1 = new AppealRequest();
        request1.setType("订单纠纷");
        request1.setReason("");
        Set<ConstraintViolation<AppealRequest>> violations1 = validator.validate(request1);
        assertFalse(violations1.isEmpty(), "reason 为空字符串时校验应该失败");
        assertTrue(violations1.stream().anyMatch(v -> v.getPropertyPath().toString().equals("reason")));

        // 2. 测试 reason 为纯空格字符串
        AppealRequest request2 = new AppealRequest();
        request2.setType("订单纠纷");
        request2.setReason("     ");
        Set<ConstraintViolation<AppealRequest>> violations2 = validator.validate(request2);
        assertFalse(violations2.isEmpty(), "reason 为纯空格时校验应该失败");

        // 3. 测试 reason 为 null
        AppealRequest request3 = new AppealRequest();
        request3.setType("订单纠纷");
        request3.setReason(null);
        Set<ConstraintViolation<AppealRequest>> violations3 = validator.validate(request3);
        assertFalse(violations3.isEmpty(), "reason 为 null 时校验应该失败");
    }

    @Test
    @DisplayName("反向测试：当所有字段都为空时，应该触发多个校验错误")
    void testAllFieldsBlank_ShouldFail() {
        AppealRequest request = new AppealRequest();
        request.setType("");
        request.setReason("");

        Set<ConstraintViolation<AppealRequest>> violations = validator.validate(request);

        // 断言：存在错误，且错误数量为 2
        assertFalse(violations.isEmpty());
        assertEquals(2, violations.size(), "应该有两个字段未通过 @NotBlank 校验");
    }
}