
package com.example.campushelp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserInfoRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;

    private String studentId;

    private String phone;

    @NotBlank(message = "学院不能为空")
    private String college;

    @NotBlank(message = "专业不能为空")
    private String major;

    private String password;
}
