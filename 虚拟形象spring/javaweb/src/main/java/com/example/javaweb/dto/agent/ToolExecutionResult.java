package com.example.javaweb.dto.agent;

import lombok.Data;

/**
 * 工具执行结果。content 会作为 tool 消息回传给模型，必须控制在预算内。
 */
@Data
public class ToolExecutionResult {
    private boolean ok;
    private String content;
    /** 给前端/日志的短摘要，不含完整数据 */
    private String summary;
    /** OK / ERROR / TIMEOUT / DENIED */
    private String status;

    public static ToolExecutionResult ok(String content, String summary) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setOk(true);
        result.setContent(content);
        result.setSummary(summary);
        result.setStatus("OK");
        return result;
    }

    public static ToolExecutionResult error(String message) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setOk(false);
        result.setContent("{\"ok\":false,\"error\":\"" + escape(message) + "\"}");
        result.setSummary(message);
        result.setStatus("ERROR");
        return result;
    }

    public static ToolExecutionResult timeout(String message) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setOk(false);
        result.setContent("{\"ok\":false,\"error\":\"" + escape(message) + "\"}");
        result.setSummary(message);
        result.setStatus("TIMEOUT");
        return result;
    }

    public static ToolExecutionResult denied(String message) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setOk(false);
        result.setContent("{\"ok\":false,\"error\":\"" + escape(message) + "\"}");
        result.setSummary(message);
        result.setStatus("DENIED");
        return result;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
