package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class NotificationResponseDTO {
    private Long id;
    private String type;
    private String title;
    private String body;
    /** payload JSON 解析后的对象，前端可直接使用 */
    private Object payload;
    private boolean read;
    private String createdAt;
}
