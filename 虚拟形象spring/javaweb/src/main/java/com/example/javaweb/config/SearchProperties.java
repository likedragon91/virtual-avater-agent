package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 联网搜索配置。API Key 只从环境变量 BOCHA_API_KEY 读取，禁止写入仓库。
 */
@Data
@ConfigurationProperties(prefix = "search")
@Component
public class SearchProperties {
    private boolean enabled = true;
    private String endpoint = "https://api.bochaai.com/v1/web-search";
    private String apiKey;
    private int count = 5;
    private int timeoutMs = 8000;
    private int cacheTtlSeconds = 300;
}
