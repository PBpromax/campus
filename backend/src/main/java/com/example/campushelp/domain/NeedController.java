package com.example.campushelp.domain;

import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.campushelp.domain.NeedContracts.*;

@RestController @RequestMapping("/api/need-sessions") @RequiredArgsConstructor
public class NeedController {
    private final NeedService service;
    private final NeedRules rules;
    @GetMapping("/catalog") public ApiResponse<List<Catalog>> catalog() { return ApiResponse.success(rules.catalog()); }
    @PostMapping public ApiResponse<Session> create() { return ApiResponse.success(service.create(SecurityUtils.getCurrentUserId())); }
    @GetMapping public ApiResponse<List<Session>> list() { return ApiResponse.success(service.list(SecurityUtils.getCurrentUserId())); }
    @GetMapping("/{id}") public ApiResponse<Session> get(@PathVariable long id) { return ApiResponse.success(service.get(SecurityUtils.getCurrentUserId(),id)); }
    @PutMapping("/{id}/category") public ApiResponse<Session> select(@PathVariable long id,@Valid @RequestBody Select request) { return ApiResponse.success(service.select(SecurityUtils.getCurrentUserId(),id,request)); }
    @PutMapping("/{id}/draft") public ApiResponse<Session> edit(@PathVariable long id,@Valid @RequestBody Edit request) { return ApiResponse.success(service.edit(SecurityUtils.getCurrentUserId(),id,request)); }
    @PostMapping("/{id}/intake") public ApiResponse<Session> intake(@PathVariable long id,@Valid @RequestBody Message request) { return ApiResponse.success(service.intake(SecurityUtils.getCurrentUserId(),id,request)); }
    @PostMapping("/{id}/messages") public ApiResponse<Session> message(@PathVariable long id,@Valid @RequestBody Message request) { return ApiResponse.success(service.message(SecurityUtils.getCurrentUserId(),id,request)); }
    @PostMapping("/{id}/confirm") public ApiResponse<Session> confirm(@PathVariable long id,@Valid @RequestBody Version request) { return ApiResponse.success(service.confirm(SecurityUtils.getCurrentUserId(),id,request)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id,@Valid @RequestBody Version request) { service.delete(SecurityUtils.getCurrentUserId(),id,request);return ApiResponse.success(null); }
}
