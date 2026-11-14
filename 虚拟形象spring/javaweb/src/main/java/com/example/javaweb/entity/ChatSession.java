package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 会话。status：1-正常，0-归档。
 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** 角色/人设编码，对应数据库 persona_code */
    private String personaCode;

    /** 1-正常，0-归档 */
    private Integer status;

    /** 历史摘要，超长上下文时使用 */
    private String summary;

    private LocalDateTime summaryUpdatedAt;

    private Integer messageCount;

    private LocalDateTime lastActiveAt;

    private LocalDateTime createdAt;
}
