package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完成度自评记录。一个 occurrence 只有一条，修正时更新同一条并写 ADJUST 流水。
 */
@Data
@TableName("task_response")
public class TaskResponseRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long taskId;

    private Long occurrenceId;

    private Integer completionRatio;

    private String note;

    private String mood;

    private LocalDateTime respondedAt;

    private Integer latencySec;

    /** 1.00 准时，0.30 补评 */
    private BigDecimal timeliness;

    private BigDecimal intimacyDelta;

    private LocalDateTime revisedAt;

    private String idempotencyKey;

    private LocalDateTime createdAt;
}
