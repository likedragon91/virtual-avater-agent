package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆条目。memory_type 受数据库 CHECK 限制：
 * PROFILE / PREFERENCE / EPISODE / RELATION / PROMISE。
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

    private LocalDateTime occurredAt;

    private Long sourceMessageId;

    private LocalDateTime embeddedAt;

    private Integer accessCount;

    private LocalDateTime lastAccessedAt;

    private BigDecimal decayScore;

    /** ACTIVE / PENDING / ARCHIVED / DELETED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
