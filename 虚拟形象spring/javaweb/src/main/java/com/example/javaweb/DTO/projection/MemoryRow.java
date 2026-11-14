package com.example.javaweb.dto.projection;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * search_memories 工具的记忆行。
 */
@Data
public class MemoryRow {
    private Long id;
    private String memoryType;
    private String title;
    private String content;
    private Integer importance;
    private LocalDateTime occurredAt;
}
