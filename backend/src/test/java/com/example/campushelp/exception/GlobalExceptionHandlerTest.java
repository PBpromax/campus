package com.example.campushelp.exception;

import com.example.campushelp.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        // 创建一个专用于测试异常的桩 Controller，并把你的全局异常处理器装配进去
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- 1. 测试自定义业务异常 ---
    @Test
    public void testHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isOk()) // 你的代码里定义的是 HttpStatus.OK
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.message", is("没有操作权限")));
    }

    // --- 2. 测试参数校验异常 ---
    @Test
    public void testHandleValidationExceptions() throws Exception {
        // 发送一个空 JSON {}，触发 @NotBlank 校验失败
        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()) // 400 Bad Request
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("参数校验失败")))
                .andExpect(jsonPath("$.data.name", is("名称不能为空"))); // 验证具体的错误字段和信息
    }

    // --- 3. 测试未知的系统异常 ---
    @Test
    public void testHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/system-error"))
                .andExpect(status().isInternalServerError()) // 500 Internal Server Error
                .andExpect(jsonPath("$.message", is("系统内部错误")));
    }

    // ==================== 专用于辅助测试的 Controller 和 DTO ====================
    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        public void throwBusinessException() {
            throw new BusinessException(401, "没有操作权限");
        }

        @PostMapping("/test/validation-error")
        public void throwValidationException(@Valid @RequestBody TestDto dto) {
            // 触发校验
        }

        @GetMapping("/test/system-error")
        public void throwSystemException() {
            throw new NullPointerException("故意制造的空指针异常");
        }
    }

    static class TestDto {
        @NotBlank(message = "名称不能为空")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}