package com.example.campushelp.service.impl;

import com.example.campushelp.dto.request.RegisterRequest;
import com.example.campushelp.dto.request.UserInfoRequest;
import com.example.campushelp.dto.response.UserResponse;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setCreditScore(100);

        User savedUser = userRepository.save(user);

        return buildUserResponse(savedUser);
    }

    @Override
    public UserResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        return buildUserResponse(user);
    }

    @Override
    public UserResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        return buildUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserInfo(Long userId, UserInfoRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setName(request.getName());
        if (request.getStudentId() != null) {
            user.setStudentId(request.getStudentId());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        user.setCollege(request.getCollege());
        user.setMajor(request.getMajor());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User savedUser = userRepository.save(user);

        return buildUserResponse(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException("用户不存在");
        }
        userRepository.deleteById(userId);
    }

    @Override
    public void checkPublishPermission(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (user.getCreditScore() < 40) {
            throw new BusinessException("您的信用分过低，账号已被封禁");
        }
        if (user.getCreditScore() < 60) {
            if (user.getRestrictedUntil() != null && user.getRestrictedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException("您的信用分不足60，在 " +
                        user.getRestrictedUntil().toLocalDate() + " 前无法发布需求");
            }
        }
        if (user.getRestrictedUntil() != null && user.getRestrictedUntil().isAfter(LocalDateTime.now())
                && user.getCreditScore() < 40) {
            throw new BusinessException("您的账号已被封禁");
        }
    }

    private UserResponse buildUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .studentId(user.getStudentId())
                .phone(user.getPhone())
                .name(user.getName())
                .college(user.getCollege())
                .major(user.getMajor())
                .creditScore(user.getCreditScore())
                .role(user.getRole())
                .restrictedUntil(user.getRestrictedUntil())
                .build();
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

}
