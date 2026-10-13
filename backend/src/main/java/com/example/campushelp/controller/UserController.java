
package com.example.campushelp.controller;

import com.example.campushelp.dto.request.RegisterRequest;
import com.example.campushelp.dto.request.UserInfoRequest;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.dto.response.LoginResponse;
import com.example.campushelp.dto.response.UserResponse;
import com.example.campushelp.service.UserService;
import com.example.campushelp.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = userService.register(request);
        String token = jwtUtil.generateToken(user.getId());
        LoginResponse loginResponse = LoginResponse.builder()
                .user(user)
                .token(token)
                .build();
        return ResponseEntity.ok(ApiResponse.success("注册成功", loginResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        UserResponse user = userService.login(username, password);
        String token = jwtUtil.generateToken(user.getId());
        
        LoginResponse loginResponse = LoginResponse.builder()
                .user(user)
                .token(token)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success("登录成功", loginResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserInfo(@PathVariable Long id) {
        UserResponse user = userService.getUserInfo(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserInfo(
            @PathVariable Long id,
            @Valid @RequestBody UserInfoRequest request) {
        UserResponse user = userService.updateUserInfo(id, request);
        return ResponseEntity.ok(ApiResponse.success("更新成功", user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }
}
