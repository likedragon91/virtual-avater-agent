package com.example.javaweb.dto.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆检索候选。score 是 MySQL ngram 全文相关度，向量重排在 Java 侧完成。
 */
@Data
public class MemoryCandidate {
    private Long id;
    private String memoryType;
    private String title;
    private String content;
    private Integer importance;
    private BigDecimal confidence;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private BigDecimal decayScore;
    private byte[] embedding;
    private String embeddingModel;
    private Integer embeddingDim;
    private Double score;
}
