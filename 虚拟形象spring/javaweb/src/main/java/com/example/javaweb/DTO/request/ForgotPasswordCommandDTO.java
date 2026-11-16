package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordCommandDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;
}
