package com.example.javaweb.dto.agent;

import lombok.Data;

/**
 * 流式响应中累积完成的一次工具调用。
 */
@Data
public class LlmToolCall {
    private String id;
    private String name;
    /** 模型返回的 JSON 字符串 */
    private String arguments;
}
