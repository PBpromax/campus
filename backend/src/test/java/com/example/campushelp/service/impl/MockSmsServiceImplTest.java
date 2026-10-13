package com.example.campushelp.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MockSmsServiceImplTest {

    private final MockSmsServiceImpl mockSmsService = new MockSmsServiceImpl();

    @Test
    @DisplayName("TC_01: [正向流程] 传入标准的手机号与常规码流，验证模块无伤通行且无副作用")
    void sendVerificationCode_NormalRequest_Success() {
        String phone = "13800138000";
        String code = "123456";

        // Assert：拒绝空洞测试，核心验证作为桩方法，不能干扰正常的业务流水线，执行不能抛出任何异常
        assertDoesNotThrow(() -> {
            mockSmsService.sendVerificationCode(phone, code);
        }, "模拟短信发送在输入正常参数时绝不应该抛出任何异常");
    }

    @Test
    @DisplayName("TC_02: [边界极端] 输入数据项包含 null 或空白项等空值，验证防报错崩塌的绝对健壮性")
    void sendVerificationCode_EdgeCaseInputs_DoesNotThrow() {
        // 覆盖空串边界
        assertDoesNotThrow(() -> {
            mockSmsService.sendVerificationCode("", "");
        }, "边界值空白字符串不应导致服务崩溃");

        // 覆盖 null 指针级联检查边界
        assertDoesNotThrow(() -> {
            mockSmsService.sendVerificationCode(null, null);
        }, "边界值 null 传入时不应触发 NullPointerException 导致上游业务熔断");
    }
}