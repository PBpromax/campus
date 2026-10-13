
package com.example.campushelp.service;

public interface SmsService {
    void sendVerificationCode(String phone, String code);
}
