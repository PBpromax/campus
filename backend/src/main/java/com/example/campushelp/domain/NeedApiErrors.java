package com.example.campushelp.domain;

import com.example.campushelp.dto.response.ApiResponse;
import com.example.campushelp.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={NeedController.class,com.example.campushelp.ai.AiController.class,com.example.campushelp.tools.ToolController.class,com.example.campushelp.observability.OperationsController.class,com.example.campushelp.transition.TaskPreviewController.class})
public class NeedApiErrors {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ResponseEntity.status(e.getCode()).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(e.getCode(),e.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(400,"请求格式无效：请检查类别、版本和字段类型"));
    }
}
