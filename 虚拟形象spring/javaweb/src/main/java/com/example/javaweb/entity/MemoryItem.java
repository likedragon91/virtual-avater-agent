package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆条目。memory_type 受数据库 CHECK 限制：
 * PROFILE / PREFERENCE / EPISODE / RELATION / PROMISE / FACT。
 */
@Data
@TableName("memory_item")
public class MemoryItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String memoryType;

    private String title;

    private String content;

    /** 扩展结构，JSON 字符串；PROMISE 的 due_at 放这里 */
    private String structured;

    /** 1~5 */
    private Integer importance;

    private BigDecimal confidence;

    /** USER_STATED / INFERRED / EXTRACTED */
    private String sourceType;

    private LocalDateTime occurredAt;

    /** 可选的过期时间；过期后不再进入检索 */
    private LocalDateTime expiresAt;

    private Long sourceMessageId;

    private LocalDateTime embeddedAt;

    private Integer accessCount;

    private LocalDateTime lastAccessedAt;

    private BigDecimal decayScore;

    /** ACTIVE / PENDING / ARCHIVED / DELETED */
    private String status;

    /** 被哪条新记忆替代；配合 status=ARCHIVED 保留历史 */
    private Long supersededBy;

    /** float32 小端向量；未配置 embedding 时为 NULL */
    private byte[] embedding;

    private String embeddingModel;

    private Integer embeddingDim;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
