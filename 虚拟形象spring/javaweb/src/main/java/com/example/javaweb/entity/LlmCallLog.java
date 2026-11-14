package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型调用日志，用于成本与延迟观测。
 */
@Data
@TableName("llm_call_log")
public class LlmCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String scene;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private BigDecimal cost;

    private String status;

    private String traceId;

    private LocalDateTime createdAt;
}
