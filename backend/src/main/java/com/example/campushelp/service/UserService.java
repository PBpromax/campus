
package com.example.campushelp.service;

import com.example.campushelp.dto.request.RegisterRequest;
import com.example.campushelp.dto.request.UserInfoRequest;
import com.example.campushelp.dto.response.UserResponse;

public interface UserService {
    UserResponse register(RegisterRequest request);
    UserResponse login(String username, String password);
    UserResponse getUserInfo(Long userId);
    UserResponse updateUserInfo(Long userId, UserInfoRequest request);
    void deleteUser(Long userId);
    void checkPublishPermission(Long userId);
}
