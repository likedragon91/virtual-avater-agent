package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 事务闹钟。status：ACTIVE / PAUSED / ARCHIVED。
 * rrule 为空表示不重复；基础支持 FREQ=DAILY/WEEKLY/MONTHLY。
 */
@Data
@TableName("task")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String note;

    private LocalDateTime dueAt;

    private String tz;

    private String rrule;

    /** 1-低 2-普通 3-高 */
    private Integer priority;

    private String status;

    /** MANUAL / AGENT */
    private String source;

    private Integer responseWindowMinutes;

    private Boolean allowNote;

    private BigDecimal intimacyWeight;

    /** JSON 字符串，默认 [0] */
    private String remindOffsets;

    private Long createdFromMessageId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 列表查询时填充，不落库 */
    @TableField(exist = false)
    private Long nextOccurrenceId;

    @TableField(exist = false)
    private LocalDateTime nextOccurrenceAt;

    @TableField(exist = false)
    private String nextOccurrenceStatus;
}
