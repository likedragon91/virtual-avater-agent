package com.example.javaweb.dto.request;


import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginCommandDTO {
    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(max = 50, message = "用户名或邮箱不能超过50个字符")
    private String username;
    //前端已用 SM3 处理，此处收到的是 64 位十六进制摘要
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "密码格式错误")
    private String password;

    //验证码ID（获取验证码接口返回）
    @Size(max = 64, message = "验证码标识过长")
    private String captchaId;

    //用户输入的验证码
    @Size(max = 10, message = "验证码长度错误")
    private String captchaCode;

    //客户端类型：DESKTOP（Electron）/ WEB（浏览器、PWA），缺省按 WEB
    @Pattern(regexp = "^(DESKTOP|WEB|desktop|web)?$", message = "客户端类型不合法")
    private String clientType;
}
