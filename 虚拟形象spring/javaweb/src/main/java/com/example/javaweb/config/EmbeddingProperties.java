package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量模型配置。默认关闭：未配置时记忆检索自动只用 ngram。
 * 连接参数（base-url / api-key）由 spring.ai.openai.* 提供。
 */
@Data
@ConfigurationProperties(prefix = "embedding")
@Component
public class EmbeddingProperties {
    private boolean enabled = false;
    /** 与 spring.ai.openai.embedding.options.model 保持一致，用于判定向量是否需要重算 */
    private String model = "";
    private int dimensions = 1024;
    /** 后台补向量时每批处理的条数 */
    private int batchSize = 50;
    private long backfillDelayMs = 60000;
}
