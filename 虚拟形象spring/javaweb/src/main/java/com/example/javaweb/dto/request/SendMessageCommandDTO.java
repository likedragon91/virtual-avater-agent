package com.example.javaweb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageCommandDTO {
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "单条消息不能超过 8000 字")
    private String content;

    /** 客户端生成，用于发送幂等 */
    @NotBlank(message = "clientMessageId 不能为空")
    @Size(max = 64, message = "clientMessageId 不能超过 64 字")
    private String clientMessageId;

    /**
     * 可选：显式指定本轮回复模式，为空时由后端自动判断。
     * 仅影响回复长度和排版，不影响事实、工具与安全规则。
     */
    @Pattern(regexp = "(?i)(CASUAL|EMOTIONAL|DISCUSSION|KNOWLEDGE|TASK)",
            message = "responseMode 取值不合法")
    private String responseMode;
}
