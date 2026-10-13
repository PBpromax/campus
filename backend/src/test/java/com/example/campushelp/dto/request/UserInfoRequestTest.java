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

class UserInfoRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化原生的 Validator 校验器，无需启动 Spring 容器，运行速度极快
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("正向测试：当所有必填字段完整且合法时，校验应该通过")
    void testValidUserInfoRequest_ShouldPass() {
        // 1. 准备完整的合法数据
        UserInfoRequest request1 = new UserInfoRequest();
        request1.setName("张三");
        request1.setStudentId("20230101");
        request1.setPhone("13800138000");
        request1.setCollege("计算机学院");
        request1.setMajor("软件工程");
        request1.setPassword("newSecurePwd123");

        Set<ConstraintViolation<UserInfoRequest>> violations1 = validator.validate(request1);
        assertTrue(violations1.isEmpty(), "完整的合法请求不应该触发校验错误");

        // 2. 准备仅包含必填字段的数据（测试可选字段为 null 的情况）
        UserInfoRequest request2 = new UserInfoRequest();
        request2.setName("李四");
        request2.setCollege("外语学院");
        request2.setMajor("英语");
        // 可选字段均留空
        request2.setStudentId(null);
        request2.setPhone(null);
        request2.setPassword(null);

        Set<ConstraintViolation<UserInfoRequest>> violations2 = validator.validate(request2);
        assertTrue(violations2.isEmpty(), "可选字段为 null 时，只要必填项合法也应该通过校验");
    }

    @Test
    @DisplayName("反向测试：当必填字段为空字符串或纯空格时，校验应该失败")
    void testBlankFields_ShouldFail() {
        UserInfoRequest request = new UserInfoRequest();
        // 故意传入空字符串或空格
        request.setName("");        // 触发 @NotBlank
        request.setCollege("   ");  // 触发 @NotBlank
        request.setMajor("");       // 触发 @NotBlank

        Set<ConstraintViolation<UserInfoRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "空必填字段应该触发校验失败");
        assertEquals(3, violations.size(), "应该有 3 个字段触发 @NotBlank 错误");

        // 验证错误消息是否包含你在源码中预设的中文提示
        boolean hasNameError = violations.stream().anyMatch(v -> v.getMessage().contains("姓名不能为空"));
        boolean hasCollegeError = violations.stream().anyMatch(v -> v.getMessage().contains("学院不能为空"));
        boolean hasMajorError = violations.stream().anyMatch(v -> v.getMessage().contains("专业不能为空"));

        assertTrue(hasNameError, "应该提示：姓名不能为空");
        assertTrue(hasCollegeError, "应该提示：学院不能为空");
        assertTrue(hasMajorError, "应该提示：专业不能为空");
    }

    @Test
    @DisplayName("反向测试：当必填字段为 null 时，校验应该失败")
    void testNullFields_ShouldFail() {
        UserInfoRequest request = new UserInfoRequest();
        // 故意将必填字段设为 null
        request.setName(null);
        request.setCollege(null);
        request.setMajor(null);

        Set<ConstraintViolation<UserInfoRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "必填字段为 null 时校验应该失败");
        assertEquals(3, violations.size(), "应该拦截到 3 个 null 值的错误");

        // 确保能精确定位到出错的属性路径
        boolean hasNamePath = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        boolean hasCollegePath = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("college"));
        boolean hasMajorPath = violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("major"));

        assertTrue(hasNamePath);
        assertTrue(hasCollegePath);
        assertTrue(hasMajorPath);
    }
}