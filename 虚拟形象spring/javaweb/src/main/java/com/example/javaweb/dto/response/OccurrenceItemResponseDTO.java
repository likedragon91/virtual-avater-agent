package com.example.javaweb.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OccurrenceItemResponseDTO {
    private Long id;
    private Long taskId;
    private String title;
    private String note;
    private Integer priority;
    private String priorityLabel;
    private String occurAt;
    private String expiresAt;
    private String status;
    private String statusLabel;
    private String respondedAt;
    private Integer responseWindowMinutes;
    private boolean canRespond;
    private boolean canCorrect;
    private boolean late;
    private Integer completionRatio;
    private String responseNote;
    private BigDecimal intimacyDelta;
}
