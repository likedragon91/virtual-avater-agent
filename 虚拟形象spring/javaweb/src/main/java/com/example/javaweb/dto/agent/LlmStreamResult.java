package com.example.javaweb.dto.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次流式模型调用的最终结果。
 */
@Data
public class LlmStreamResult {
    private String content = "";
    private List<LlmToolCall> toolCalls = new ArrayList<>();
    private String finishReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private long latencyMs;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
