package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次具体的提醒实例。status：PENDING / RINGING / RESPONDED / MISSED / CANCELED。
 */
@Data
@TableName("task_occurrence")
public class TaskOccurrence {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    private LocalDateTime occurAt;

    private LocalDateTime expiresAt;

    private String status;

    private LocalDateTime firedAt;

    private LocalDateTime respondedAt;

    private LocalDateTime createdAt;
}
