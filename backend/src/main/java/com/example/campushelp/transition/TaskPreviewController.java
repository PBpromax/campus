package com.example.campushelp.transition;

import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.example.campushelp.transition.TaskPreviewContracts.*;

@RestController @RequestMapping("/api/task-transition-preview") @RequiredArgsConstructor
public class TaskPreviewController {
    private final TaskPreviewService service;
    private final CangjieTaskPreview component;
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status() {return ApiResponse.success(Map.of("configured",component.configured(),"mode","READ_ONLY"));}
    @PostMapping public ApiResponse<Preview> preview(@Valid @RequestBody Request request) {return ApiResponse.success(service.preview(SecurityUtils.getCurrentUserId(),request));}
}
