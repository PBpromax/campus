
package com.example.campushelp.service.impl;

import com.example.campushelp.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "sms.enabled", havingValue = "false", matchIfMissing = true)
public class MockSmsServiceImpl implements SmsService {

    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("【模拟短信发送】验证码: {} 发送到手机: {}", code, phone);
        log.info("提示：如需真实发送短信，请配置阿里云短信服务参数并设置 sms.enabled=true");
    }
}
