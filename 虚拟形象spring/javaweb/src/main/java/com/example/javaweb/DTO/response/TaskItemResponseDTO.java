package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class TaskItemResponseDTO {
    private Long id;
    private String title;
    private String note;
    private String dueAt;
    private String tz;
    private String rrule;
    private String rruleLabel;
    private Integer priority;
    private String priorityLabel;
    private String status;
    private String statusLabel;
    private String source;
    private Integer responseWindowMinutes;
    private Boolean allowNote;
    private Long nextOccurrenceId;
    private String nextOccurrenceAt;
    private String nextOccurrenceStatus;
    private String createdAt;
}
