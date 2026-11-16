package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class ChatSessionResponseDTO {
    private Long id;
    private String title;
    private String personaCode;
    private Integer status;
    private Integer messageCount;
    private String lastActiveAt;
    private String createdAt;
}
