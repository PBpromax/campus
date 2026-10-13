package com.example.campushelp.config;

import com.example.campushelp.entity.User;
import com.example.campushelp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Component
@ConditionalOnProperty(name = "demo.seed-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("testuser1")) {
            User testUser = new User();
            testUser.setUsername("testuser1");
            testUser.setStudentId("2024001");
            testUser.setPhone("13800138000");
            testUser.setPassword(passwordEncoder.encode("123456"));
            testUser.setName("测试同学");
            testUser.setCollege("计算机学院");
            testUser.setMajor("软件工程");
            userRepository.save(testUser);
        }

        if (!userRepository.existsByUsername("testuser2")) {
            User testUser2 = new User();
            testUser2.setUsername("testuser2");
            testUser2.setStudentId("2024002");
            testUser2.setPhone("13900139000");
            testUser2.setPassword(passwordEncoder.encode("123456"));
            testUser2.setName("李同学");
            testUser2.setCollege("经济管理学院");
            testUser2.setMajor("工商管理");
            userRepository.save(testUser2);
        }

        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setStudentId("admin001");
            admin.setPhone("13700137000");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setName("管理员");
            admin.setCollege("信息中心");
            admin.setMajor("系统管理");
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }

        log.info("=============================================");
        log.info("测试账号:");
        log.info("  账号1 - 用户名: testuser1  密码: 123456  (普通用户)");
        log.info("  账号2 - 用户名: testuser2  密码: 123456  (普通用户)");
        log.info("  管理员 - 用户名: admin  密码: 123456  (管理员)");
        log.info("=============================================");
    }
}