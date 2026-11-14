package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息。sender_role：user / assistant / system / tool。
 * status：0-生成中，1-完成，2-失败，3-已取消（仅 assistant 消息使用）。
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String senderRole;

    private String content;

    private String contentType;

    private Integer status;

    /** 客户端幂等键，与 session_id 组成唯一索引 */
    private String clientMessageId;

    /** 情绪 JSON，可为空 */
    private String emotion;

    private Integer tokens;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
