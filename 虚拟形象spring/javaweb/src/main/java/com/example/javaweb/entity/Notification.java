package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。type：TASK_REMINDER 等；payload 存关联信息。
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String body;

    /** JSON 字符串 */
    private String payload;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
