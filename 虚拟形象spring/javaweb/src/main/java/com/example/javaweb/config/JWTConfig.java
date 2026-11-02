package com.example.javaweb.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "jwt")
@Component
@Data
public class JWTConfig {
    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String tokenPrefix;
    private String header;

}
