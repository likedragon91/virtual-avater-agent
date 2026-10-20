package com.example.javaweb.DTO.command;


import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginCommandDTO {
    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(max = 10,message = "用户名或邮箱不能超过10个字符")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(max = 10,min=6,message = "密码长度必须在6到10个字符之间")
    private String password;

    public Object getUsername() {
        return username;
    }
}
