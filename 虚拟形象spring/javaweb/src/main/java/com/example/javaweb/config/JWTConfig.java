package com.example.javaweb.config;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
@Component
@Data
public class JWTConfig {
    @NotBlank(message = "必须通过环境变量 JWT_SECRET 配置 JWT 签名密钥")
    @Size(min = 32, message = "JWT_SECRET 长度至少为 32 个字符")
    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String tokenPrefix;
    private String header;

}
