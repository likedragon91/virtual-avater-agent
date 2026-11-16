package com.example.javaweb.dto.projection;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * query_tasks 工具的任务行。
 */
@Data
public class TaskRow {
    private Long id;
    private String title;
    private String note;
    private LocalDateTime dueAt;
    private String tz;
    private Integer priority;
    private String status;
    private String rrule;
    private Long occurrenceId;
    private LocalDateTime occurAt;
    private String occurrenceStatus;
    private LocalDateTime expiresAt;
}
