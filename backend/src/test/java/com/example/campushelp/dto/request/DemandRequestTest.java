package com.example.campushelp.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DemandRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化原生的 Validator 校验器，无需启动 Spring 容器，运行速度极快
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("正向测试：所有参数合法时，校验应该通过")
    void testValidDemandRequest_ShouldPass() {
        // 准备合法的请求数据
        DemandRequest request = new DemandRequest();
        request.setTitle("代拿快递");
        request.setDescription("帮我拿一下西区驿站的快递，谢谢");
        request.setCategory("日常跑腿");
        request.setExpectedTime(LocalDateTime.now().plusHours(2));
        request.setReward(new BigDecimal("5.00"));
        request.setLocation("西区宿舍楼");

        // 执行校验
        Set<ConstraintViolation<DemandRequest>> violations = validator.validate(request);

        // 断言：不应该有任何校验错误
        assertTrue(violations.isEmpty(), "合法的请求不应该有校验错误");
    }

    @Test
    @DisplayName("反向测试：必填字符串字段为空或空格时，校验应该失败")
    void testBlankFields_ShouldFail() {
        DemandRequest request = new DemandRequest();
        // 故意传入空字符串或纯空格，触发 @NotBlank 校验
        request.setTitle("");
        request.setDescription("   ");
        request.setCategory("");
        request.setExpectedTime(LocalDateTime.now().plusHours(2));
        request.setReward(new BigDecimal("5.00"));
        request.setLocation("");

        Set<ConstraintViolation<DemandRequest>> violations = validator.validate(request);

        // 断言：校验必须失败
        assertFalse(violations.isEmpty(), "空字段应该触发校验失败");
        assertEquals(4, violations.size(), "应该有4个字段触发 @NotBlank 错误");

        // 验证错误消息是否包含预设的中文提示
        boolean hasTitleError = violations.stream().anyMatch(v -> v.getMessage().contains("标题不能为空"));
        boolean hasDescError = violations.stream().anyMatch(v -> v.getMessage().contains("描述不能为空"));
        boolean hasCategoryError = violations.stream().anyMatch(v -> v.getMessage().contains("类别不能为空"));
        boolean hasLocationError = violations.stream().anyMatch(v -> v.getMessage().contains("位置不能为空"));

        assertTrue(hasTitleError, "应该提示：标题不能为空");
        assertTrue(hasDescError, "应该提示：描述不能为空");
        assertTrue(hasCategoryError, "应该提示：类别不能为空");
        assertTrue(hasLocationError, "应该提示：位置不能为空");
    }

    @Test
    @DisplayName("反向测试：期望时间和报酬为 null 时，校验应该失败")
    void testNullExpectedTimeAndReward_ShouldFail() {
        DemandRequest request = new DemandRequest();
        request.setTitle("代打热水");
        request.setDescription("拿开水瓶打水");
        request.setCategory("日常跑腿");
        request.setLocation("东区12栋");

        // 故意设为 null，触发 @NotNull 校验
        request.setExpectedTime(null);
        request.setReward(null);

        Set<ConstraintViolation<DemandRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        boolean hasTimeError = violations.stream().anyMatch(v -> v.getMessage().contains("期望完成时间不能为空"));
        boolean hasRewardError = violations.stream().anyMatch(v -> v.getMessage().contains("报酬不能为空"));

        assertTrue(hasTimeError, "应该提示：期望完成时间不能为空");
        assertTrue(hasRewardError, "应该提示：报酬不能为空");
    }

    @Test
    @DisplayName("边界值测试：报酬为负数或零时，校验应该失败")
    void testInvalidReward_ShouldFail() {
        DemandRequest request = new DemandRequest();
        request.setTitle("帮买午饭");
        request.setDescription("一号食堂二楼盖浇饭");
        request.setCategory("日常跑腿");
        request.setLocation("教学楼A栋");
        request.setExpectedTime(LocalDateTime.now().plusHours(1));

        // 1. 测试负数报酬，触发 @Positive 校验
        request.setReward(new BigDecimal("-2.50"));
        Set<ConstraintViolation<DemandRequest>> violationsNegative = validator.validate(request);
        assertFalse(violationsNegative.isEmpty());
        assertTrue(violationsNegative.stream().anyMatch(v -> v.getMessage().contains("报酬必须为正数")));

        // 2. 测试零元报酬（@Positive 不包含 0），同样应该触发校验失败
        request.setReward(BigDecimal.ZERO);
        Set<ConstraintViolation<DemandRequest>> violationsZero = validator.validate(request);
        assertFalse(violationsZero.isEmpty());
        assertTrue(violationsZero.stream().anyMatch(v -> v.getMessage().contains("报酬必须为正数")));
    }
}