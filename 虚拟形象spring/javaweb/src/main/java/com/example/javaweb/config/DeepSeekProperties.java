package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 配置。apiKey 只从环境变量 DEEPSEEK_API_KEY 读取，禁止写入仓库。
 */
@Data
@ConfigurationProperties(prefix = "deepseek")
@Component
public class DeepSeekProperties {
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-chat";
    private int maxTokens = 4096;
    private Double temperature = 0.8;
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 120000;
    private int maxRetries = 1;
    private boolean includeUsage = true;
}
