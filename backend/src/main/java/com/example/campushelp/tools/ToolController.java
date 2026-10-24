package com.example.campushelp.tools;
import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.campushelp.tools.ToolContracts.*;

@RestController @RequestMapping("/api/trusted-tools") @RequiredArgsConstructor
public class ToolController {
    private final ToolService service;
    private final ToolTransactions transactions;
    @PostMapping("/prepare") public ApiResponse<Receipt> prepare(@Valid @RequestBody Prepare request) {return ApiResponse.success(service.prepare(SecurityUtils.getCurrentUserId(),request));}
    @PostMapping("/{id}/execute") public ApiResponse<Receipt> execute(@PathVariable String id,@Valid @RequestBody Confirm request) {return ApiResponse.success(service.execute(SecurityUtils.getCurrentUserId(),id,request));}
    @PostMapping("/{id}/revoke") public ApiResponse<Receipt> revoke(@PathVariable String id) {return ApiResponse.success(service.revoke(SecurityUtils.getCurrentUserId(),id));}
    @GetMapping("/{id}") public ApiResponse<Receipt> get(@PathVariable String id) {return ApiResponse.success(transactions.get(SecurityUtils.getCurrentUserId(),id));}
    @GetMapping public ApiResponse<List<Receipt>> list() {return ApiResponse.success(transactions.list(SecurityUtils.getCurrentUserId()));}
    @GetMapping("/tasks") public ApiResponse<List<TaskSummary>> tasks(@RequestParam(defaultValue="available") String scope) {return ApiResponse.success(transactions.tasks(SecurityUtils.getCurrentUserId(),scope));}
    @GetMapping("/audits") public ApiResponse<List<ToolAudit>> audits() {return ApiResponse.success(transactions.auditList(SecurityUtils.getCurrentUserId()));}
}
