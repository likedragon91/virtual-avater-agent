package com.example.javaweb.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行上下文。userId 由服务端注入，模型无法修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {
    private Long userId;
    private Long sessionId;
    /** assistant 消息 id，即 runId */
    private Long assistantMessageId;
    /** 触发本轮回答的用户消息 id，用于 save_memory 的来源关联 */
    private Long userMessageId;
}
