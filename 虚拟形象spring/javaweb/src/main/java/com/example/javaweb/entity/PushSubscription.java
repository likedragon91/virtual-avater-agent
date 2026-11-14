package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览器 PushSubscription。一个用户可以有多台设备/多个浏览器。
 */
@Data
@TableName("push_subscription")
public class PushSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String endpoint;

    private String p256dh;

    private String auth;

    private String userAgent;

    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;
}
