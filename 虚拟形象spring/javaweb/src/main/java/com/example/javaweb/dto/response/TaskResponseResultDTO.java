package com.example.javaweb.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaskResponseResultDTO {
    private Long responseId;
    private Long taskId;
    private Long occurrenceId;
    private Integer completionRatio;
    private BigDecimal intimacyDelta;
    private BigDecimal intimacyTotal;
    private String stageCode;
    private String stageName;
    private BigDecimal dailyGained;
    private Integer dailyCap;
    private Long taskCountToday;
    private Integer maxDailyTasks;
    private String feedback;
    private boolean corrected;
    private String respondedAt;
}
