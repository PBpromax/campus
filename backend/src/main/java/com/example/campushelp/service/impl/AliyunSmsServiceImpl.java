package com.example.campushelp.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.example.campushelp.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary
public class AliyunSmsServiceImpl implements SmsService {

    @Value("${sms.aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${sms.aliyun.sign-name}")
    private String signName;

    @Value("${sms.aliyun.template-code}")
    private String templateCode;

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (!smsEnabled) {
            log.info("短信服务未启用，验证码: {} 发送到手机: {}", code, phone);
            return;
        }

        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "dysmsapi.aliyuncs.com";

            Client client = new Client(config);

            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(request);

            if ("OK".equals(response.body.code)) {
                log.info("短信发送成功，验证码: {} 发送到手机: {}", code, phone);
            } else {
                log.error("短信发送失败，错误码: {}, 错误信息: {}", response.body.code, response.body.message);
                throw new RuntimeException("短信发送失败: " + response.body.message);
            }
        } catch (Exception e) {
            log.error("短信发送异常", e);
            throw new RuntimeException("短信发送异常: " + e.getMessage());
        }
    }
}