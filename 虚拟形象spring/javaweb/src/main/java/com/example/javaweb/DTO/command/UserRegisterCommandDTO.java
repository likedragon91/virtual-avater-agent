package com.example.javaweb.DTO.command;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data

public class UserRegisterCommandDTO {

    //{
    //    "username": "string",
    //    "email": "string",
    //    "nickname": "string",
    //    "phone": "string",
    //    "password": "string",
    //    "confirmPassword": "string",
    //    "gender": 0,
    //    "userType": 0
    //}
    @NotBlank(message = "用户名不能为空")
    @Size(min=3,max=50,message = "用户名长度必须在3到50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$",message = "用户名只能包含字母、数字和下划线")

    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    @Size(max=50,message = "邮箱长度不能超过50")

    private String email;

    @Size(max=50,message = "昵称长度不能超过50")

    private String nickname;

    @Pattern(regexp = "^1[3456789]\\d{9}$",message = "手机号格式错误")

    private String phone;

    //前端已用 SM3 处理，此处收到的是 64 位十六进制摘要
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "密码格式错误")

    private String password;

    @NotBlank(message = "确认密码不能为空")

    private String confirmPassword;

    private Integer gender;
    //公开注册固定为普通用户(1)，该字段不再生效，保留仅为兼容旧请求
    private Integer userType=1;
}
