package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class NotificationListResponseDTO {
    private List<NotificationResponseDTO> records;
    private long total;
    private long page;
    private long size;
    private long unreadCount;
}
