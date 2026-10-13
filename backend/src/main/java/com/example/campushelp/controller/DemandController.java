package com.example.campushelp.controller;

import com.example.campushelp.dto.request.CancelRequest;
import com.example.campushelp.dto.request.CompleteRequest;
import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.dto.response.DemandResponse;
import com.example.campushelp.service.DemandService;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demands")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandController {

    private final DemandService demandService;

    @PostMapping
    public ResponseEntity<ApiResponse<DemandResponse>> createDemand(
            @Valid @RequestBody DemandRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DemandResponse demand = demandService.createDemand(userId, request);
        return ResponseEntity.ok(ApiResponse.success("发布成功", demand));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DemandResponse>> getDemandById(@PathVariable Long id) {
        DemandResponse demand = demandService.getDemandById(id);
        return ResponseEntity.ok(ApiResponse.success(demand));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DemandResponse>>> getAllDemands(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<DemandResponse> demands = demandService.getAllDemands(category, status, keyword);
        return ResponseEntity.ok(ApiResponse.success(demands));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<DemandResponse>>> getDemandsByStatus(@PathVariable String status) {
        List<DemandResponse> demands = demandService.getDemandsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(demands));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<DemandResponse>>> getDemandsByCategory(@PathVariable String category) {
        List<DemandResponse> demands = demandService.getDemandsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(demands));
    }

    @GetMapping("/publisher/{publisherId}")
    public ResponseEntity<ApiResponse<List<DemandResponse>>> getDemandsByPublisher(@PathVariable Long publisherId) {
        List<DemandResponse> demands = demandService.getDemandsByPublisher(publisherId);
        return ResponseEntity.ok(ApiResponse.success(demands));
    }

    @GetMapping("/acceptor/{acceptorId}")
    public ResponseEntity<ApiResponse<List<DemandResponse>>> getDemandsByAcceptor(@PathVariable Long acceptorId) {
        List<DemandResponse> demands = demandService.getDemandsByAcceptor(acceptorId);
        return ResponseEntity.ok(ApiResponse.success(demands));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<DemandResponse>> acceptDemand(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        DemandResponse demand = demandService.acceptDemand(id, userId);
        return ResponseEntity.ok(ApiResponse.success("接单成功", demand));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<DemandResponse>> completeDemand(
            @PathVariable Long id,
            @RequestBody CompleteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DemandResponse demand = demandService.completeDemand(id, userId, request.getProof());
        return ResponseEntity.ok(ApiResponse.success("完成凭证已提交，等待发布者确认", demand));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<DemandResponse>> confirmDemand(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        DemandResponse demand = demandService.confirmDemand(id, userId);
        return ResponseEntity.ok(ApiResponse.success("已确认完成", demand));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<DemandResponse>> cancelDemand(
            @PathVariable Long id,
            @RequestBody(required = false) CancelRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String reason = request != null ? request.getReason() : null;
        DemandResponse demand = demandService.cancelDemand(id, userId, reason);
        return ResponseEntity.ok(ApiResponse.success("取消成功", demand));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDemand(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        demandService.deleteDemand(id, userId);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }
}
