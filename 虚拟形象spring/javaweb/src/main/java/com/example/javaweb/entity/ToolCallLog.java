package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用审计。messageId 即本次 runId（assistant 消息 id）。
 */
@Data
@TableName("tool_call_log")
public class ToolCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private Long sessionId;

    private Long userId;

    private String callId;

    private String toolName;

    /** 脱敏后的参数，JSON 字符串 */
    private String arguments;

    private String resultSummary;

    /** OK / ERROR / TIMEOUT / DENIED */
    private String status;

    private Integer durationMs;

    private LocalDateTime createdAt;
}
