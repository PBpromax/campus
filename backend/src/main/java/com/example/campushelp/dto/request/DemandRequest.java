
package com.example.campushelp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DemandRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    
    @NotBlank(message = "描述不能为空")
    private String description;
    
    @NotBlank(message = "类别不能为空")
    private String category;
    
    @NotNull(message = "期望完成时间不能为空")
    private LocalDateTime expectedTime;
    
    @NotNull(message = "报酬不能为空")
    @Positive(message = "报酬必须为正数")
    private BigDecimal reward;
    
    @NotBlank(message = "位置不能为空")
    private String location;
}
