package com.example.campushelp.observability;

import com.example.campushelp.ai.ModelObservations;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ops") @RequiredArgsConstructor
public class OperationsController {
    private final UserRepository users;
    private final ModelObservations observations;
    private final com.example.campushelp.ai.ModelCircuit circuit;
    @GetMapping("/model-circuit") public ApiResponse<com.example.campushelp.ai.ModelCircuit.View> circuit() {
        if(!users.findById(SecurityUtils.getCurrentUserId()).map(u->"ADMIN".equals(u.getRole())).orElse(false)) throw new BusinessException(403,"仅管理员可查看运行观测");
        return ApiResponse.success(circuit.view());
    }
    @GetMapping("/model-observations") public ApiResponse<ModelObservations.Snapshot> modelObservations() {
        // JWT currently contains only user ID. Recheck the persisted role, never a caller header or claim.
        if(!users.findById(SecurityUtils.getCurrentUserId()).map(u->"ADMIN".equals(u.getRole())).orElse(false))
            throw new BusinessException(403,"仅管理员可查看运行观测");
        return ApiResponse.success(observations.snapshot());
    }
}
