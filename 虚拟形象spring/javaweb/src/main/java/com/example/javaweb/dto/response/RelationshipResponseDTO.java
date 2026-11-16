package com.example.javaweb.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelationshipResponseDTO {
    private BigDecimal intimacyTotal;
    private String stageCode;
    private String stageName;
    private String stageSince;
    private String nextStageCode;
    private String nextStageName;
    private BigDecimal nextThreshold;
    private double progressPercent;
    private BigDecimal todayGained;
    private Integer dailyCap;
    private Long taskCountToday;
    private Integer maxDailyTasks;
    private Integer streakDays;
    private String userCapStage;
}
