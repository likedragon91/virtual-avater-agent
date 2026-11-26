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
    /** 上下文代理（billion-context）开关；未启动时按 fallbackToDirect 回退 */
    private boolean proxyEnabled = true;
    /** 代理地址，形如 http://127.0.0.1:8787/bili/https://api.deepseek.com */
    private String proxyUrl = "http://127.0.0.1:8787/bili/https://api.deepseek.com";
    private boolean fallbackToDirect = true;
    /** 代理首字节超时；比直连短，代理卡住时尽快回退 */
    private int proxyTimeoutMs = 30000;
    private String model = "deepseek-chat";
    private int maxTokens = 4096;
    private Double temperature = 0.8;
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 120000;
    private int maxRetries = 1;
    private boolean includeUsage = true;
}
