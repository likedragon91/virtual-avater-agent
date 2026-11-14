package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 亲密度流水。reason：TASK_RESPONSE / ADJUST / LEVEL_UP / DAILY_BONUS。
 */
@Data
@TableName("intimacy_log")
public class IntimacyLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal delta;

    private BigDecimal balanceAfter;

    private String reason;

    private String refType;

    private Long refId;

    private LocalDateTime createdAt;
}
