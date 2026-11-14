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
    private String createdAt;
}
