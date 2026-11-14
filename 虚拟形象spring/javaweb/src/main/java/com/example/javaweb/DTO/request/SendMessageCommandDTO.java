package com.example.javaweb.dto.request;

import jakarta.validation.constraints.NotBlank;
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
}
