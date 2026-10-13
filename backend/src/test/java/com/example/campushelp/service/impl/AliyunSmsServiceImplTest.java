package com.example.campushelp.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody; // 1. 修正为正确的 ResponseBody 类
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AliyunSmsServiceImplTest {

    @InjectMocks
    private AliyunSmsServiceImpl aliyunSmsService;

    private final String phone = "13800000000";
    private final String code = "123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aliyunSmsService, "accessKeyId", "testAccessKeyId");
        ReflectionTestUtils.setField(aliyunSmsService, "accessKeySecret", "testAccessKeySecret");
        ReflectionTestUtils.setField(aliyunSmsService, "signName", "测试签名");
        ReflectionTestUtils.setField(aliyunSmsService, "templateCode", "SMS_123456");
    }

    @Test
    @DisplayName("当短信服务未启用时，应该直接返回不发送短信")
    void testSendVerificationCode_SmsDisabled() {
        ReflectionTestUtils.setField(aliyunSmsService, "smsEnabled", false);

        assertDoesNotThrow(() -> aliyunSmsService.sendVerificationCode(phone, code));
    }

    @Test
    @DisplayName("当短信服务启用且阿里云返回OK时，应该发送成功")
    void testSendVerificationCode_Success() throws Exception {
        ReflectionTestUtils.setField(aliyunSmsService, "smsEnabled", true);

        // 2. 修复类名错误并正确构造 Body
        SendSmsResponse mockResponse = new SendSmsResponse();
        SendSmsResponseBody mockBody = new SendSmsResponseBody();
        mockBody.setCode("OK");
        mockBody.setMessage("OK");
        mockResponse.setBody(mockBody);

        // 3. 优化为表达式 Lambda 且使用 getFirst() 消除警告
        try (MockedConstruction<Client> mockedClient = mockConstruction(Client.class,
                (mock, context) -> when(mock.sendSms(any(SendSmsRequest.class))).thenReturn(mockResponse))) {

            aliyunSmsService.sendVerificationCode(phone, code);

            assertEquals(1, mockedClient.constructed().size());
            Client clientInstance = mockedClient.constructed().getFirst(); // 4. 优化：使用 getFirst() 替换 get(0)
            verify(clientInstance, times(1)).sendSms(any(SendSmsRequest.class));
        }
    }

    @Test
    @DisplayName("当阿里云返回错误码时，应该抛出 RuntimeException")
    void testSendVerificationCode_AliyunReturnError() {
        ReflectionTestUtils.setField(aliyunSmsService, "smsEnabled", true);

        SendSmsResponse mockResponse = new SendSmsResponse();
        SendSmsResponseBody mockBody = new SendSmsResponseBody();
        mockBody.setCode("isv.MOBILE_NUMBER_ILLEGAL");
        mockBody.setMessage("手机号码格式错误");
        mockResponse.setBody(mockBody);

        try (MockedConstruction<Client> ignored = mockConstruction(Client.class, // 5. 变量名改为 ignored 解决未使用警告
                (mock, context) -> when(mock.sendSms(any(SendSmsRequest.class))).thenReturn(mockResponse))) {

            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                    aliyunSmsService.sendVerificationCode(phone, code)
            );

            assertTrue(exception.getMessage().contains("短信发送失败: 手机号码格式错误"));
        }
    }

    @Test
    @DisplayName("当阿里云 SDK 内部抛出异常时，应该捕获并封装为 RuntimeException 抛出")
    void testSendVerificationCode_ThrowsException() {
        ReflectionTestUtils.setField(aliyunSmsService, "smsEnabled", true);

        try (MockedConstruction<Client> ignored = mockConstruction(Client.class,
                (mock, context) -> when(mock.sendSms(any(SendSmsRequest.class))).thenThrow(new RuntimeException("Timeout")))) {

            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                    aliyunSmsService.sendVerificationCode(phone, code)
            );

            assertTrue(exception.getMessage().contains("短信发送异常: Timeout"));
        }
    }
}