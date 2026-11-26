package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class ChatMessageResponseDTO {
    private Long id;
    private String senderRole;
    private String content;
    private String contentType;
    private Integer status;
    private Integer tokens;
    private String model;
    /** 情绪 JSON，如 {"emotion":"joy"}；历史里用于恢复表情 */
    private String emotion;
    private String createdAt;
}
