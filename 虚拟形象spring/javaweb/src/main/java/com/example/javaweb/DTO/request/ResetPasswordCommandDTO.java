package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordCommandDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须是 6 位数字")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "新密码格式错误")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "确认密码格式错误")
    private String confirmPassword;
}
